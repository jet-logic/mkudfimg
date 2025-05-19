package mkimg;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Node<T> extends Iterable<Node<T>> {

    public String getName();

    public String getPath();

    default public boolean isLeaf() {
        return true;
    }

    public T getData();

    public void setData(T data);

    public Node<T> getParent();

    default public Node<T> getRoot() {
        Node<T> cur = this;
        Node<T> parent;
        for (;;) {
            parent = cur.getParent();
            if (parent == null) {
                return cur;
            }
            cur = parent;
        }
    }

    static class EmptyIterator<T> implements Iterator<Node<T>> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Node<T> next() {
            return null;
        }
    }

    @Override
    default public Iterator<Node<T>> iterator() {
        return new EmptyIterator();
    }

    default public List<Node<T>> getChildren() {
        throw new UnsupportedOperationException();
    }

    default public <V> V getAttribute(Class<V> type) {
        throw new UnsupportedOperationException();
    }

    default public Iterator<Node<T>> depthFirstIterator() {
        return new PostorderEnum(this);
    }

    default public Iterable<Node<T>> depthFirstIterable() {
        return () -> new PostorderEnum(this);
    }

    default public Stream<Node<T>> depthFirst() {
        return StreamSupport.stream(depthFirstIterable().spliterator(), false);
    }

    default public Iterator<Node<T>> ascentIterator() {
        return new Ascent(this);
    }

    default public void descend(Consumer<Node<T>> x) {
        Node<T> parent = this.getParent();
        if (parent != null) {
            parent.descend(x);
        }
        x.accept(this);
    }

    default public String getPath(char sep) {
        StringBuilder sb = new StringBuilder();
        this.descend(x -> {
            String name = x.getName();
            if (x.getParent() != null) {
                sb.append(sep);
            }
            if (name != null) {
                sb.append(name);
            }
        });
        return sb.toString();
    }

    class Ascent<V> implements Iterator<Node<V>> {

        Node<V> cur;

        public Ascent(Node<V> cur) {
            this.cur = cur;
        }

        @Override
        public boolean hasNext() {
            return null != cur;
        }

        @Override
        public Node<V> next() {
            Node<V> give = cur;
            cur = cur.getParent();
            return give;
        }

    }

    static class PostorderEnum<V extends Iterable> implements Iterator<V> {

        protected V root;
        protected Iterator<V> children;
        protected Iterator<V> subtree;

        public PostorderEnum(V rootNode) {
            super();
            root = rootNode;
            children = root.iterator();
            subtree = Collections.emptyIterator();
        }

        @Override
        public boolean hasNext() {
            return root != null;
        }

        @Override
        public V next() {
            V retval;

            if (subtree.hasNext()) {
                retval = subtree.next();
            } else if (children.hasNext()) {
                subtree = new PostorderEnum(children.next());
                retval = subtree.next();
            } else {
                retval = root;
                root = null;
            }

            return retval;
        }

    }
}
