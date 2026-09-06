package jd.controlling.packagecontroller;

import org.jdownloader.DomainInfo;
import org.jdownloader.controlling.UniqueAlltimeID;

public interface AbstractPackageChildrenNode<E> extends AbstractNode {

    E getParentNode();

    void setParentNode(E parent);

    UniqueAlltimeID getPreviousParentNodeID();

    public DomainInfo getDomainInfo();

    public boolean hasVariantSupport();

    /**
     * Returns a stable identifier that is used to detect duplicates. Two nodes with the same linkID are considered the same file, even if
     * their URLs differ (e.g. different mirrors, or the same source processed into different variants).
     *
     * @return the duplicate-detection ID of this node
     */
    public String getLinkID();
}
