package searchengine.utils;

import lombok.Data;
import searchengine.models.SiteEntity;

import java.util.HashSet;
import java.util.Set;

@Data
public class HashSetPages {
    private static Set<String> setUrls;

    public static HashSetPages getInstance() {
        Set<String> set = new HashSet<>();
        return new HashSetPages(set);
    }

    public HashSetPages(Set<String> setUrls) {
        HashSetPages.setUrls = setUrls;
    }

    public void addObject(String url) {
        setUrls.add(url);
    }

    public boolean notContainsObject(String pages) {
        return !setUrls.contains(pages);
    }

    public void removalIndexedSite(SiteEntity site) {
        String homePage = site.getUrl().substring(0, site.getUrl().length() - 1);
        setUrls.removeIf(x -> x.startsWith(homePage));
    }

    public void clear() {
        HashSetPages.setUrls.clear();
    }

    public int size() {
        return setUrls.size();
    }
}
