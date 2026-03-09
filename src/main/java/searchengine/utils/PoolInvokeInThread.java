package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import searchengine.config.Site;
import searchengine.dto.SiteStatus;
import searchengine.models.SiteEntity;
import searchengine.repository.SiteRepository;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Slf4j
public class PoolInvokeInThread implements Runnable {
    Site site;
    RecursiveSiteLink recursiveSiteLink;
    Set<SiteEntity> hashSetSiteEntities;
    private final HashSetPages hashSetPages = HashSetPages.getInstance();
    private final SiteRepository siteRepository;
    private final InterrupterPool interrupterPool = new InterrupterPool();
    private final ForkJoinPool pool = new ForkJoinPool();

    public PoolInvokeInThread(RecursiveSiteLink recursiveSiteLink,
                              Set<SiteEntity> hashSetSiteEntities,
                              Site site,
                              SiteRepository siteRepository) {
        this.recursiveSiteLink = recursiveSiteLink;
        this.hashSetSiteEntities = hashSetSiteEntities;
        this.site = site;
        this.siteRepository = siteRepository;
    }

    @Override
    public void run() {
        pool.invoke(recursiveSiteLink);
        hashSetSiteEntities.forEach(siteEntity -> {
            if (siteEntity.getUrl().equals(site.getUrl()) && !siteEntity.getStatus().equals(SiteStatus.FAILED)) {
                siteEntity.setStatus(SiteStatus.INDEXED);
                siteRepository.save(siteEntity);
                System.out.println(siteEntity.getUrl());
                hashSetPages.removalIndexedSite(siteEntity);
            }
        });
        getFinishIfPoolIsQuiescent();
    }

    private void getFinishIfPoolIsQuiescent() {
        if (pool.isQuiescent() && hashSetPages.size() == 0) {
            interrupterPool.setStop(true);
            System.out.println("INDEXING FINISHED");
        } else {
            System.out.println("indexed");
        }
    }
}
