package searchengine.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "pages", indexes = @Index(name = "idx_pages_path", columnList = "path, site_id", unique = true)) //todo переделать индекс на "path"
public class PageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(foreignKey = @ForeignKey(name = "site_page_FK"), columnDefinition = "Integer",
            referencedColumnName = "id", name = "site_id", nullable = false, updatable = false) //todo может убрать этот индекс?
    private SiteEntity siteId;

    @Column(columnDefinition = "VARCHAR(768) CHARACTER SET utf8", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
