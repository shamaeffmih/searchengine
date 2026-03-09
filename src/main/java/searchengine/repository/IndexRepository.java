package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.models.IndexEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    boolean existsByLemmaIdAndPageId(LemmaEntity lemmaId, PageEntity pageId);

    IndexEntity findByLemmaIdAndPageId(LemmaEntity lemmaId, PageEntity pageId);

    List<IndexEntity> findAllByLemmaId(LemmaEntity lemmaEntity);
}
