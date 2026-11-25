package br.edu.atitus.orderapi.repositories;

import br.edu.atitus.orderapi.entities.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserIdOrderByIdDesc(Long userId);
    List<OrderEntity> findAllByOrderByIdDesc();

    /** Pedidos que contêm ao menos um item vendido por {@code sellerId}. */
    @Query("select distinct o from OrderEntity o join o.items i where i.sellerId = :sellerId order by o.id desc")
    List<OrderEntity> findBySeller(@Param("sellerId") Long sellerId);
}
