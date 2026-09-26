package com.roti5dao.catalog.repository;

import com.roti5dao.catalog.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p from Product p join fetch p.category c
            where p.active = true and c.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (:recommended is null or p.recommended = :recommended)
              and (:pattern is null
                   or lower(p.name) like :pattern escape '\\'
                   or lower(coalesce(p.description, '')) like :pattern escape '\\')
            order by c.sortOrder, c.id, p.sortOrder, p.id
            """)
    List<Product> findPublic(Long categoryId, Boolean recommended, String pattern);

    @Query("select p from Product p join fetch p.category c where p.id = :id and p.active = true and c.active = true")
    Optional<Product> findPublicById(Long id);

    @Query("select p from Product p join fetch p.category where p.id in :ids")
    List<Product> findWithCategoryByIdIn(Collection<Long> ids);

    @Query(value = """
            select p from Product p join fetch p.category c
            where (:categoryId is null or c.id = :categoryId)
              and (:active is null or p.active = :active)
              and (:pattern is null
                   or lower(p.name) like :pattern escape '\\'
                   or lower(p.code) like :pattern escape '\\')
            """,
            countQuery = """
            select count(p) from Product p join p.category c
            where (:categoryId is null or c.id = :categoryId)
              and (:active is null or p.active = :active)
              and (:pattern is null
                   or lower(p.name) like :pattern escape '\\'
                   or lower(p.code) like :pattern escape '\\')
            """)
    Page<Product> searchAdmin(Long categoryId, Boolean active, String pattern, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
