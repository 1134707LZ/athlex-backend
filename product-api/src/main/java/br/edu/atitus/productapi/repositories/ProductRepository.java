package br.edu.atitus.productapi.repositories;

import br.edu.atitus.productapi.entities.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    /** Parâmetros nunca nulos ("" = sem filtro): evita "lower(bytea)" do PostgreSQL com parâmetro null. */
    @Query("""
           select p from ProductEntity p
           where (:category = '' or p.category = :category)
             and (:q = '' or lower(p.name) like lower(concat('%', :q, '%'))
                             or lower(p.description) like lower(concat('%', :q, '%')))
           order by p.id desc
           """)
    List<ProductEntity> search(@Param("q") String q, @Param("category") String category);

    List<ProductEntity> findByOwnerIdOrderByIdDesc(Long ownerId);

    /** Baixa de estoque atômica: só atualiza se houver saldo suficiente (retorna 0 caso contrário). */
    @Modifying
    @Query("update ProductEntity p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int reserve(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("update ProductEntity p set p.stock = p.stock + :qty where p.id = :id")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
