package com.roti5dao.banner.repository;

import com.roti5dao.banner.entity.Banner;
import com.roti5dao.banner.entity.BannerPlacement;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("""
            select b from Banner b
            where b.placement = :placement and b.active = true
              and (b.startAt is null or b.startAt <= :now) and (b.endAt is null or b.endAt > :now)
            order by b.sortOrder asc, b.id asc
            """)
    List<Banner> findVisible(BannerPlacement placement, Instant now);

    @Query("""
            select b from Banner b
            where (:placement is null or b.placement = :placement) and (:active is null or b.active = :active)
            order by b.placement asc, b.sortOrder asc, b.id asc
            """)
    List<Banner> search(BannerPlacement placement, Boolean active);

    List<Banner> findByPlacementOrderBySortOrderAscIdAsc(BannerPlacement placement);

    @Query("select coalesce(max(b.sortOrder), -1) from Banner b where b.placement = :placement")
    int maxSortOrder(BannerPlacement placement);

    @Query("select count(b) > 0 from Banner b where b.id <> :excludeId and (b.imageUrl = :url or b.imageUrlMobile = :url)")
    boolean isImageUsedByOther(String url, Long excludeId);
}
