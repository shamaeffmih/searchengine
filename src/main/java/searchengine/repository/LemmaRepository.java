package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.LemmaEntity;
import searchengine.models.SiteEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Boolean existsByLemmaAndSiteId(String lemma, SiteEntity siteId);

    List<LemmaEntity> findBySiteIdIn(Collection<SiteEntity> siteEntities);

    LemmaEntity findByLemmaAndSiteId(String lemma, SiteEntity siteId);

    int countLemmasBySiteId(SiteEntity siteId);
}
