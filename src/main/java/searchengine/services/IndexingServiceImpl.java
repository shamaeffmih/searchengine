package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.models.SiteEntity;
import searchengine.dto.SiteStatus;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final ParserSettings parserSettings;
    private SiteEntity siteEntity;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private final HashSetPages hashSetPages = HashSetPages.getInstance();

    private final InterrupterPool interrupterPool = new InterrupterPool();

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (!checkingCanStartIndexing(response)) {
            return response;
        }
        prepareForIndexing();
        List<Site> sitesList = sites.getSites();
        Set<SiteEntity> hashSetSiteEntities = new HashSet<>();
        processSites(sitesList, hashSetSiteEntities);
        return response;
    }

    private boolean checkingCanStartIndexing(IndexingResponse response) {
        if (interrupterPool.isStop()) {
            response.setResult(true);
            interrupterPool.setStop(false);
            return true;
        } else {
            response.setError("Индексация уже запущена");
            response.setResult(false);
            return false;
        }
    }

    private void prepareForIndexing() {
        removingDataFromPreviousIndexing();
    }

    private void processSites(List<Site> sitesList, Set<SiteEntity> hashSetSiteEntities) {
        for (Site site : sitesList) {
            processSingleSite(site, hashSetSiteEntities);
        }
    }

    private void processSingleSite(Site site, Set<SiteEntity> hashSetSiteEntities) {
        saveIndexingSiteEntity(site);
        hashSetSiteEntities.add(siteEntity);
        if (!verifyIsSiteAccessible(site)) {
            return;
        }
        startIndexingForSite(site, hashSetSiteEntities);
    }

    private boolean verifyIsSiteAccessible(Site site) {
        ExceptionHandlers exceptionHandlers = new ExceptionHandlers(siteEntity, siteRepository);
        try {
            Jsoup.connect(site.getUrl())
                    .userAgent(UserAgents.getUserAgent())
                    .maxBodySize(0)
                    .get();
            return true;
        } catch (HttpStatusException eh) {
            exceptionHandlers.handleHttpStatusException(eh);
        } catch (SocketTimeoutException es) {
            exceptionHandlers.handleTimeoutException(es);
        } catch (IOException ei) {
            exceptionHandlers.handleIOException(ei);
        } catch (Exception e) {
            exceptionHandlers.handleGeneralException(e);
        }
        return false;
    }

    private void startIndexingForSite(Site site, Set<SiteEntity> hashSetSiteEntities) {
        Link rootLink = new Link(site.getUrl());
        RecursiveSiteLink recursiveSiteLink = new RecursiveSiteLink(
                rootLink, parserSettings, siteRepository, pageRepository,
                lemmaRepository, indexRepository, siteEntity, interrupterPool, hashSetPages
        );
        PoolInvokeInThread poolInvokeInThreads = new PoolInvokeInThread(
                recursiveSiteLink, hashSetSiteEntities, site, siteRepository
        );
        new Thread(poolInvokeInThreads).start();
    }


    private void removingDataFromPreviousIndexing() {
        log.info("Удаление данных от предыдущей индексации start");
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        log.info("Удаление данных от предыдущей индексации end");
    }

    @Override
    public IndexingResponse stopIndexing() {
        interrupterPool.setStop(true);
        System.out.println("Пользователь нажал \"STOP INDEXING\" ");
        IndexingResponse response = new IndexingResponse();
        if (!interrupterPool.isStop()) {
            response.setError("Индексация не запущена");
            response.setResult(false);
        } else {
            response.setResult(true);
        }
        return response;
    }

    private void saveIndexingSiteEntity(Site i) {
        siteEntity = new SiteEntity();
        siteEntity.setName(i.getName());
        siteEntity.setUrl(i.getUrl());
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }
}
