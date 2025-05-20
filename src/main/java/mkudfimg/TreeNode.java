package mkudfimg;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TreeNode implements Node<Inode> {

    static char SEPARATOR = '/';
    String name;
    String path;
    TreeNode parent;
    Inode data;
    public int flag = 0;
    static final int IGNORE = 1;
    static final int HIDE = 2;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public Inode getData() {
        return data;
    }

    @Override
    public void setData(Inode data) {
        this.data = data;
    }

    public TreeNode getChild(String name) {
        for (Node<Inode> child : this) {
            if (child.getName().equals(name)) {
                return (TreeNode) child;
            }
        }
        return null;
    }

    public boolean isHidden() {
        return (HIDE & flag) == HIDE;
    }

    public void writeTree(final PrintStream out, final int depth) throws IOException {
        if (depth > 0) {
            for (int i = depth; i-- > 0;) {
                out.write(' ');
            }
            if (this.isLeaf()) {
                out.write('-');
            } else {
                out.write('+');
            }
            out.print(' ');
            out.print(getName());
            out.print('\n');
        }
        if (!this.isLeaf()) {
            final int i = depth + 1;
            for (Node<Inode> child : this) {
                ((TreeNode) child).writeTree(out, i);
            }
        }
    }

    public TreeNode internTree(String name) {
        if (name.indexOf(SEPARATOR) >= 0) {
            throw new RuntimeException("Name has separator : \"" + name + "\"");
        }
        TreeNode child = (TreeNode) getChild(name);
        if (child == null) {
            child = new Directory(name, this);
            this.getChildren().add(child);

        } else if (child.isLeaf()) {
            throw new RuntimeException("Directory expected : \"" + name + "\"");
        }
        return child;
    }

    public TreeNode internFile(String name) {
        if (name.indexOf(SEPARATOR) >= 0) {
            throw new RuntimeException("Name has separator : \"" + name + "\"");
        }
        TreeNode child = getChild(name);
        if (child == null) {
            child = new File(name, this);
            this.getChildren().add(child);
        } else if (!child.isLeaf()) {
            throw new RuntimeException("File expected : \"" + name + "\"");
        }
        return child;
    }

    static public class Directory extends TreeNode {

        private final List<Node<Inode>> children;

        public Directory(String name, TreeNode parent) {
            this.name = name;
            this.parent = parent;
            this.children = new LinkedList<>();
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public List<Node<Inode>> getChildren() {
            return children;
        }

        @Override
        public Iterator<Node<Inode>> iterator() {
            return this.children.iterator();
        }
    }

    static public class File extends TreeNode {

        File(String name, TreeNode parent) {
            this.name = name;
            this.parent = parent;
        }

    }
}
