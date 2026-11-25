package br.edu.atitus.orderapi.services;

import br.edu.atitus.orderapi.dtos.Dtos.*;
import br.edu.atitus.orderapi.entities.OrderEntity;
import br.edu.atitus.orderapi.entities.OrderItemEntity;
import br.edu.atitus.orderapi.repositories.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {
    private static final Set<String> PAGAMENTOS = Set.of("CARTAO", "PIX", "BOLETO");

    private final OrderRepository repo;
    private final AuthorizationService auth;
    private final ProductClient products;

    public OrderService(OrderRepository repo, AuthorizationService auth, ProductClient products) {
        this.repo = repo;
        this.auth = auth;
        this.products = products;
    }

    public OrderResponse create(OrderRequest r, Long userId) {
        if (r == null) throw bad("Requisição inválida");
        validateAddress(r.endereco());
        if (r.pagamento() == null || !PAGAMENTOS.contains(r.pagamento())) throw bad("Forma de pagamento inválida");
        if (r.itens() == null || r.itens().isEmpty()) throw bad("Carrinho vazio");

        var order = new OrderEntity();
        order.setUserId(userId);
        order.setPayment(r.pagamento());
        order.setStatus(r.pagamento().equals("CARTAO") ? "EM_TRANSITO" : "AGUARDANDO_PAGAMENTO");
        var a = r.endereco();
        order.setZip(a.cep()); order.setStreet(a.rua()); order.setNumber(a.numero());
        order.setCity(a.cidade()); order.setState(a.uf());

        // 1) valida tudo e monta os itens (preço SEMPRE vem do product-api, nunca do cliente)
        double total = 0;
        for (var i : r.itens()) {
            if (i.produtoId() == null || i.quantidade() == null || i.quantidade() < 1) throw bad("Item inválido");
            ProductInfo p = fetchProduct(i.produtoId());
            if (p.estoque() < i.quantidade()) throw bad("Estoque insuficiente: " + p.nome());

            var c = i.personalizacao();
            String nome = null, numero = null;
            if (c != null) {
                if (!p.personalizavel()) throw bad("Produto não aceita personalização: " + p.nome());
                nome = c.nome(); numero = c.numero();
            }
            var it = new OrderItemEntity();
            it.setProductId(i.produtoId());
            it.setSellerId(p.vendedorId() == null ? null : Long.valueOf(p.vendedorId()));
            it.setProductName(p.nome());
            it.setUnitPrice(PricingCalculator.unitPrice(p.preco(), nome, numero));
            it.setQuantity(i.quantidade());
            it.setSize(i.tamanho());
            it.setColor(i.cor());
            if (c != null) { it.setCustomModel(c.modelo()); it.setCustomName(c.nome()); it.setCustomNumber(c.numero()); }
            order.getItems().add(it);
            total += it.getUnitPrice() * it.getQuantity();
        }
        order.setTotal(PricingCalculator.round2(total));

        // 2) baixa de estoque; se algo falhar, devolve o que já foi reservado (compensação)
        List<OrderItemEntity> reserved = new ArrayList<>();
        try {
            for (var it : order.getItems()) {
                reserve(it.getProductId(), it.getQuantity());
                reserved.add(it);
            }
            return toResponse(repo.save(order));
        } catch (RuntimeException e) {
            reserved.forEach(it -> release(it.getProductId(), it.getQuantity()));
            throw e;
        }
    }

    /** Comprador vê apenas os próprios pedidos; administrador vê todos. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long userId, Integer type) {
        var list = auth.isAdmin(type) ? repo.findAllByOrderByIdDesc() : repo.findByUserIdOrderByIdDesc(userId);
        return list.stream().map(OrderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, Long userId, Integer type) {
        var o = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        auth.requireOwnerOrAdmin(o.getUserId(), userId, type);
        return toResponse(o);
    }

    /** Histórico de vendas do vendedor: apenas os itens dos produtos dele (admin vê tudo). */
    @Transactional(readOnly = true)
    public List<SaleResponse> sales(Long sellerId, Integer type) {
        auth.requireSeller(type);
        boolean admin = auth.isAdmin(type);
        var orders = admin ? repo.findAllByOrderByIdDesc() : repo.findBySeller(sellerId);
        return orders.stream().map(o -> {
            var mine = o.getItems().stream().filter(i -> admin || sellerId.equals(i.getSellerId())).toList();
            double subtotal = PricingCalculator.round2(mine.stream().mapToDouble(i -> i.getUnitPrice() * i.getQuantity()).sum());
            return new SaleResponse("PED-" + (1000 + o.getId()), o.getCreatedAt().toString(), o.getStatus(), subtotal,
                    mine.stream().map(i -> new ItemResponse(i.getProductName(), i.getQuantity(), i.getUnitPrice())).toList());
        }).toList();
    }

    @Transactional(readOnly = true)
    public SalesSummary summary(Long sellerId, Integer type) {
        var s = sales(sellerId, type);
        return new SalesSummary(s.size(), PricingCalculator.round2(s.stream().mapToDouble(SaleResponse::subtotal).sum()));
    }

    // ---------- integração com o product-api (resiliente: ver ProductClient) ----------
    private ProductInfo fetchProduct(Long id) {
        try {
            return products.find(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw bad("Produto " + id + " não existe");
        }
    }

    private void reserve(Long id, int qty) {
        try {
            products.reserve(id, qty);
        } catch (HttpClientErrorException.Conflict e) {
            throw bad("Estoque insuficiente para o produto " + id);
        }
    }

    private void release(Long id, int qty) {
        products.release(id, qty);
    }

    private void validateAddress(AddressDto a) {
        if (a == null || a.cep() == null || a.cep().replaceAll("\\D", "").length() != 8) throw bad("CEP inválido");
        if (blank(a.rua()) || blank(a.numero()) || blank(a.cidade())) throw bad("Endereço incompleto");
        if (a.uf() == null || a.uf().trim().length() != 2) throw bad("UF inválida");
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    static OrderResponse toResponse(OrderEntity o) {
        return new OrderResponse("PED-" + (1000 + o.getId()), o.getCreatedAt().toString(), o.getTotal(), o.getStatus(),
                o.getItems().stream().map(i -> new ItemResponse(i.getProductName(), i.getQuantity(), i.getUnitPrice())).toList());
    }
}
