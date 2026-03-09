package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {

    boolean existsByPathAndSiteId(String path, SiteEntity siteId);

    PageEntity findByPathAndSiteId(String urlPage, SiteEntity siteId);

    int countPagesBySiteId(SiteEntity siteId);
}
