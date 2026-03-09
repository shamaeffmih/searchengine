package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.index.IndexPageResponse;
import searchengine.models.IndexEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.LemmaFinder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IndexPageServiceIml implements IndexPageService {
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    @Autowired
    private final SitesList sites = new SitesList();
    private PageEntity pageEntity;
    private String urlSite;
    private String urlPage;
    private SiteEntity siteId;
    private PageEntity pageId;
    IndexPageResponse response = new IndexPageResponse();

    @Override
    @Transactional
    public IndexPageResponse getIndexPage(String indexPage) {
        SiteEntity siteEntity;
        if (checkingValiditySite(indexPage).isResult()) {
            urlPage = indexPage.substring(urlSite.length() - 1);
            siteEntity = siteRepository.findByUrl(urlSite);
            siteId = siteRepository.getReferenceById(siteEntity.getId());
        } else {
            return response;
        }
        removingIfPathAndSiteExists(siteEntity);

        if (!pageRepository.existsByPathAndSiteId(urlPage, siteId)) {
            PageEntity pageEntity = setPageEntity(indexPage);
            pageRepository.save(pageEntity);
            pageId = pageRepository.getReferenceById(pageEntity.getId());

            handlerLemmaAndIndex();
        }
        return response;
    }

    private IndexPageResponse checkingValiditySite(String indexPage) {
        for (Site s : sites.getSites()) {
            if (indexPage.startsWith(s.getUrl())) {
                response.setResult(true);
                response.setError(null);
                urlSite = s.getUrl();
                break;
            } else {
                response.setResult(false);
                response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            }
        }
        return response;
    }

//    private void handlerLemmaAndIndex() {
//        LemmaFinder lemmaFinder = getLemmaFinder();
//        try {
//            lemmaFinder = LemmaFinder.getInstance();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//        String cleanPage = lemmaFinder.cleanUpCodePage(pageEntity);
//        for (Map.Entry<String, Integer> entry : lemmaFinder.collectLemmas(cleanPage).entrySet()) {
//            String lemma = entry.getKey();
//            Integer lemmasCount = entry.getValue();
//            if (!lemmaRepository.existsByLemmaAndSiteId(lemma, siteId)) {
//                LemmaEntity lemmaEntity = setLemmaEntity(lemma);
//                lemmaId = lemmaRepository.getReferenceById(lemmaEntity.getId());
//            } else {
//                LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
//                int frequency = lemmaEntity.getFrequency();
//                lemmaEntity.setFrequency(++frequency);
//                lemmaRepository.save(lemmaEntity);
//                lemmaId = lemmaRepository.getReferenceById(lemmaEntity.getId());
//            }
//            if (!indexRepository.existsByLemmaIdAndPageId(lemmaId, pageId)) {
//                setIndexEntity(lemmasCount);
//            } else {
//                IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaId, pageId);
//                indexEntity.setRank(lemmasCount);
//                indexRepository.save(indexEntity);
//            }
//        }
//    }

    private void handlerLemmaAndIndex() {
        LemmaFinder lemmaFinder = getLemmaFinder();
        String cleanPage = lemmaFinder.cleanUpCodePage(pageEntity);

        for (Map.Entry<String, Integer> entry : lemmaFinder.collectLemmas(cleanPage).entrySet()) {
            String lemma = entry.getKey();
            Integer lemmasCount = entry.getValue();

            int lemmaId = getLemmaIdAndSaveLemma(lemma, siteId);
            processIndex(lemmaId, pageId, lemmasCount);
        }
    }
    private LemmaFinder getLemmaFinder() {
        try {
            return LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private int getLemmaIdAndSaveLemma(String lemma, SiteEntity siteId) {
        if (!lemmaRepository.existsByLemmaAndSiteId(lemma, siteId)) {
            LemmaEntity lemmaEntity = setLemmaEntity(lemma);
            return lemmaEntity.getId();
        } else {
            LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
            int frequency = lemmaEntity.getFrequency();
            lemmaEntity.setFrequency(++frequency);
            lemmaRepository.save(lemmaEntity);
            return lemmaEntity.getId();
        }
    }

    private void processIndex(Integer lemmaId, PageEntity pageId, Integer lemmasCount) {
        LemmaEntity lemmaEntity = lemmaRepository.getReferenceById(lemmaId);

        if (!indexRepository.existsByLemmaIdAndPageId(lemmaEntity, pageId)) {
            setIndexEntity(lemmasCount, lemmaEntity);
        } else {
            IndexEntity indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity, pageId);
            indexEntity.setRank(lemmasCount);
            indexRepository.save(indexEntity);
        }
    }

    private PageEntity setPageEntity(String indexPage) {
        Document doc;
        try {
            doc = Jsoup.connect(indexPage).get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PageEntity pageEntity = new PageEntity();
        pageEntity.setCode(doc.connection().response().statusCode());
        pageEntity.setContent(doc.html());
        pageEntity.setPath(urlPage);
        pageEntity.setSiteId(siteId);
        return pageEntity;
    }

    private LemmaEntity setLemmaEntity(String lemma) {
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        lemmaEntity.setSiteId(siteId);
        lemmaRepository.save(lemmaEntity);
        return lemmaEntity;
    }

    private void setIndexEntity(Integer lemmasCount, LemmaEntity lemmaId) {
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setLemmaId(lemmaId);
        indexEntity.setPageId(pageId);
        indexEntity.setRank(lemmasCount);
        indexRepository.save(indexEntity);
    }

    private void removingIfPathAndSiteExists(SiteEntity siteEntity) {
        if (pageRepository.existsByPathAndSiteId(urlPage, siteId)) {
            pageEntity = pageRepository.findByPathAndSiteId(urlPage, siteId);
            pageRepository.deleteById(pageEntity.getId());

            List<SiteEntity> ListSitesId = siteRepository.findAllById(siteEntity.getId());
            List<LemmaEntity> listLemmaBySitesId = lemmaRepository.findBySiteIdIn(ListSitesId);
            listLemmaBySitesId.forEach(l -> {
                int freq = l.getFrequency();
                l.setFrequency(--freq);
                if (freq == 0) lemmaRepository.deleteById(l.getId());
            });
        }
    }
}
