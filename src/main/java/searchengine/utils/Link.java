package searchengine.utils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Link {
    private final String link;
    private final List<Link> children;

    public Link(String link) {
        this.link = link;
        this.children = new CopyOnWriteArrayList<>();
    }

    public List<Link> getChildren() {
        return new ArrayList<>(children);
    }

    public String getLinkString() {
        return link;
    }

    public void addChildren(Link element) {
        if (!children.contains(element)) {
            children.add(element);
        }
    }

    public void removeChildren(Link element) {
        children.remove(element);
    }

    public List<Link> isEmptyLink() {
        return new ArrayList<>();
    }
}
