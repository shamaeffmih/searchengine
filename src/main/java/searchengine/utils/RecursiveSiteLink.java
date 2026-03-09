package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import searchengine.config.ParserSettings;
import searchengine.models.IndexEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.dto.SiteStatus;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
public class RecursiveSiteLink extends RecursiveTask<List<String>> {

    private final Link link;
    private final ParserSettings parserSettings;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteEntity siteEntity;
    private LemmaEntity lemmaEntity;
    private IndexEntity indexEntity;
    private LemmaFinder lemmaFinder;

    private final InterrupterPool interrupterPool;
    private final Pattern extensions = Pattern.compile(".*(\\.(css|js|xml|gif|jpg|jpeg|fig|png|mp3|mp4|avi|zip|gz|pdf|doc|docx|xls|xlsx|txt|eps|webp|dat|nc))$");
    public static int count = 0;
    private final HashSetPages hashSetPages;
//    private final HashSetPagesEntity hashSetPagesEntity;
//    private ArrayList<String> linkString;
//    private final ReadWriteLock lock = new ReentrantReadWriteLock();
//    String siteUrlWithoutSlash;

//    @Override
//    @Transactional
//    public List<String> compute() {
//        String url = link.getLinkString();
//        String childLink;
//        String pathPage = "";
//        log.info("url - " + url);
//        SiteEntity siteId;
//        try {
//            Document doc = loadDocumentFromURL(url);
//            Elements elements = doc.select("a[href]");
////            if (elements.isEmpty()){
////                return Collections.emptyList();
////            }
//            for (Element element : elements) {
//                if (interrupterPool.isStop()) {
//                    setAndSaveStatusInterruptFromUser();
//                    link.isEmptyLink();
//                    break;
//                }
//                interrupterPool.setStop(false);
//
//                childLink = element.attr("abs:href").toLowerCase();
////                if (childLink.isEmpty()){
////                    return Collections.emptyList();
////                }
//                String siteUrlWithoutSlash = siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1); //TODO Может вывести за compute() &?
//
//                if (childLink.startsWith(siteUrlWithoutSlash)) {
//                    pathPage = childLink.substring(siteUrlWithoutSlash.length());
//                    siteId = siteRepository.getReferenceById(siteEntity.getId());
//                    if (pageRepository.existsByPathAndSiteId(pathPage, siteId)){
//                        link.removeChildren(link);
//                        continue;
//                    }
//                } else {
//                    continue;
//                }
//                if (childLink.startsWith(siteUrlWithoutSlash)
//                        && hashSetPages.NotContainsObject(childLink)
//                        && !extensions.matcher(childLink).find()
//                        && !childLink.contains("?") && !childLink.contains("#")
//                ) {
//                    hashSetPages.addObject(childLink);
//                    count++;
//                    log.info("childLink - " + childLink);
//                    link.addChildren(new Link(childLink));
//                    siteEntity.setStatusTime(LocalDateTime.now());
//                    siteRepository.save(siteEntity);
//                    Document documentPage = loadDocumentFromURL(childLink);
//                    int responseStatusCode = documentPage.connection().response().statusCode();
//                    String content = documentPage.html();
//
//                    PageEntity pageEntity = new PageEntity();
//                    setPageEntityFields(pageEntity, siteId, content, responseStatusCode, pathPage);
//                    PageEntity pageId;
//                    if (!pageRepository.existsByPathAndSiteId(pathPage, siteId)) {
//                        pageRepository.save(pageEntity);
//                        pageId = pageRepository.getReferenceById(pageEntity.getId());
//                    } else {
//                        continue;
//                    }
//                    saveLemmaEntityAndIndexEntity(siteId, pageId, pageEntity);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        try {
//            sleep(5000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        return forkJoinTasks();
//    }

    @Override
    @Transactional
    public List<String> compute() {
        try {
            String url = link.getLinkString();
//            log.info("url - " + url);
            Document doc = loadDocumentFromURL(url);
            Elements elements = doc.select("a[href]");

            if (elements.isEmpty()) {
                return Collections.emptyList();
            }

            processElements(elements);
        } catch (Exception e) {
            e.printStackTrace();
        }
//        sleepSafely();
        return forkJoinTasks();
    }

    private void processElements(Elements elements) {
        String siteUrlWithoutSlash = siteEntity.getUrl().substring(0, siteEntity.getUrl().length() - 1);
        SiteEntity siteId = siteRepository.getReferenceById(siteEntity.getId());

        for (Element element : elements) {
            if (shouldInterruptProcessing()) {
                break;
            }

            String childLink = extractChildLink(element);
            if (childLink.isEmpty()) {
                return;
            }

            handleChildLink(childLink, siteUrlWithoutSlash, siteId);
        }
    }

    private boolean shouldInterruptProcessing() {
        if (interrupterPool.isStop()) {
            setAndSaveStatusInterruptFromUser();
            link.isEmptyLink();
            return true;
        }
        interrupterPool.setStop(false);
        return false;
    }

    private String extractChildLink(Element element) {
        return element.attr("abs:href").toLowerCase();
    }

    private void handleChildLink(String childLink, String siteUrlWithoutSlash, SiteEntity siteId) {
        if (!childLink.startsWith(siteUrlWithoutSlash)) {
            return;
        }

        String pathPage = childLink.substring(siteUrlWithoutSlash.length());
        if (pageRepository.existsByPathAndSiteId(pathPage, siteId)) {
            link.removeChildren(link);
            return;
        }

        if (isValidChildLink(childLink)) {
            processValidChildLink(childLink, pathPage, siteId);
        }
    }

    private boolean isValidChildLink(String childLink) {
        return hashSetPages.notContainsObject(childLink)
                && !extensions.matcher(childLink).find()
                && !childLink.contains("?")
                && !childLink.contains("#");
    }

    private void processValidChildLink(String childLink, String pathPage, SiteEntity siteId) {
        hashSetPages.addObject(childLink);
        count++;
        log.info("childLink - " + childLink);
        link.addChildren(new Link(childLink));
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);

        Document documentPage;
        try {
            documentPage = loadDocumentFromURL(childLink);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int responseStatusCode = documentPage.connection().response().statusCode();
        String content = documentPage.html();

        PageEntity pageEntity = new PageEntity();
        setPageEntityFields(pageEntity, siteId, content, responseStatusCode, pathPage);
        savePageAndRelatedEntities(pageEntity, siteId, pathPage);
    }

    private void savePageAndRelatedEntities(PageEntity pageEntity, SiteEntity siteId, String pathPage) {
        if (!pageRepository.existsByPathAndSiteId(pathPage, siteId)) {
            pageRepository.save(pageEntity);
            PageEntity pageId = pageRepository.getReferenceById(pageEntity.getId());
            try {
                saveLemmaEntityAndIndexEntity(siteId, pageId, pageEntity);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void sleepSafely() {
        try {
            sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Document loadDocumentFromURL(String url) throws IOException {
            return Jsoup.connect(url)
                    .userAgent(UserAgents.getUserAgent())
                    .timeout(parserSettings.getTimeout())
                    .ignoreContentType(parserSettings.isIgnoreContentType())
                    .ignoreHttpErrors(parserSettings.isIgnoreHttpErrors())
                    .followRedirects(parserSettings.isFollowRedirects())
                    .get();
    }

    private List<String> forkJoinTasks() {
        List<RecursiveSiteLink> tasksList = new ArrayList<>();
        List<String> linkString = new ArrayList<>();
        if (link.getChildren().size() == 0){
            link.removeChildren(link);
        }
        for (Link child : link.getChildren()) {
            RecursiveSiteLink task = new RecursiveSiteLink(child, parserSettings, siteRepository, pageRepository, lemmaRepository, indexRepository, siteEntity, interrupterPool, hashSetPages);
            task.fork();
            tasksList.add(task);
        }
        for (RecursiveSiteLink task : tasksList) {
            linkString.addAll(task.join());
        }
        return linkString;
    }

//    private void saveLemmaEntityAndIndexEntity(SiteEntity siteId, PageEntity pageId, PageEntity pageEntity) throws IOException {
//        lemmaFinder = LemmaFinder.getInstance();
//        String cleanPage = lemmaFinder.cleanUpCodePage(pageEntity);
//        for (Map.Entry<String, Integer> entry : lemmaFinder.collectLemmas(cleanPage).entrySet()) {
//            String lemma = entry.getKey();
//            Integer lemmasCount = entry.getValue();
//
//            if (!lemmaRepository.existsByLemmaAndSiteId(lemma, siteId)) {
//                setNewLemmaEntityFields(lemma, siteId);
//            } else {
//                lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
//                int frequency = lemmaEntity.getFrequency();
//                lemmaEntity.setFrequency(++frequency);
//            }
//            lemmaRepository.saveAndFlush(lemmaEntity);
//            LemmaEntity lemmaId = lemmaRepository.getReferenceById(lemmaEntity.getId());
//
//            if (!indexRepository.existsByLemmaIdAndPageId(lemmaId, pageId)) {
//                setNewIndexEntityFields(lemmaId, pageId);
//            } else {
//                indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaId, pageId);
//            }
//            indexEntity.setRank(lemmasCount);
//            indexRepository.saveAndFlush(indexEntity);
//        }
//    }

    private void saveLemmaEntity(SiteEntity siteId, String lemma) {
        if (!lemmaRepository.existsByLemmaAndSiteId(lemma, siteId)) {
            setNewLemmaEntityFields(lemma, siteId);
        } else {
            lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
            int frequency = lemmaEntity.getFrequency();
            lemmaEntity.setFrequency(++frequency);
        }
        lemmaRepository.saveAndFlush(lemmaEntity);
    }
    private void saveIndexEntity(PageEntity pageId, LemmaEntity lemmaId, Integer lemmasCount) {
        if (!indexRepository.existsByLemmaIdAndPageId(lemmaId, pageId)) {
            setNewIndexEntityFields(lemmaId, pageId);
        } else {
            indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaId, pageId);
        }
        indexEntity.setRank(lemmasCount);
        indexRepository.saveAndFlush(indexEntity);
    }
    private void saveLemmaEntityAndIndexEntity(SiteEntity siteId, PageEntity pageId, PageEntity pageEntity) throws IOException {
        lemmaFinder = LemmaFinder.getInstance();
        String cleanPage = lemmaFinder.cleanUpCodePage(pageEntity);

        for (Map.Entry<String, Integer> entry : lemmaFinder.collectLemmas(cleanPage).entrySet()) {
            String lemma = entry.getKey();
            Integer lemmasCount = entry.getValue();

            saveLemmaEntity(siteId, lemma);
            LemmaEntity lemmaId = lemmaRepository.getReferenceById(lemmaEntity.getId());
            saveIndexEntity(pageId, lemmaId, lemmasCount);
        }
    }

    private void setNewLemmaEntityFields(String lemma, SiteEntity siteId) {
        lemmaEntity = new LemmaEntity();
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        lemmaEntity.setSiteId(siteId);
    }

    private void setNewIndexEntityFields(LemmaEntity lemmaId, PageEntity pageId) {
        indexEntity = new IndexEntity();
        indexEntity.setLemmaId(lemmaId);
        indexEntity.setPageId(pageId);
    }

    private void setPageEntityFields(PageEntity pageEntity, SiteEntity siteId,
                                     String content, int responseStatusCode, String pathPage) {
        pageEntity.setSiteId(siteId);
        pageEntity.setContent(content);
        pageEntity.setCode(responseStatusCode);
        pageEntity.setPath(pathPage);
    }

    private void setAndSaveStatusInterruptFromUser() {
        siteEntity.setStatus(SiteStatus.FAILED);
        siteEntity.setLastError("Индексация прервана пользователем");
        siteRepository.save(siteEntity);
    }
}
