package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;
import org.apache.jasper.Options;

public class TextOptimizer {

    /**
     * 联系相邻模板文本的访问者.
     */
    static class TextCatVisitor extends Node.Visitor {

        private Options options;
        private int textNodeCount = 0;
        private Node.TemplateText firstTextNode = null;
        private StringBuffer textBuffer;
        private final String emptyText = new String("");

        public TextCatVisitor(Compiler compiler) {
            options = compiler.getCompilationContext().getOptions();
        }

        public void doVisit(Node n) throws JasperException {
            collectText();
        }

        /*
         * 在文本连接中忽略下列指令
         */
        public void visit(Node.PageDirective n) throws JasperException {
        }

        public void visit(Node.TagDirective n) throws JasperException {
        }

        public void visit(Node.TaglibDirective n) throws JasperException {
        }

        public void visit(Node.AttributeDirective n) throws JasperException {
        }

        public void visit(Node.VariableDirective n) throws JasperException {
        }

        /*
         * 不要将文本跨越主体的界限
         */
        public void visitBody(Node n) throws JasperException {
            super.visitBody(n);
            collectText();
        }

        public void visit(Node.TemplateText n) throws JasperException {

            if (options.getTrimSpaces() && n.isAllSpace()) {
                n.setText(emptyText);
                return;
            }

            if (textNodeCount++ == 0) {
                firstTextNode = n;
                textBuffer = new StringBuffer(n.getText());
            } else {
                // Append text to text buffer
                textBuffer.append(n.getText());
                n.setText(emptyText);
            }
        }

        /**
         * 此方法中断级联模式. 作为副作用，它将级联字符串复制到第一个文本节点 
         */
        private void collectText() {

            if (textNodeCount > 1) {
                // 将缓冲区中的文本复制到第一个模板文本节点中.
                firstTextNode.setText(textBuffer.toString());
            }
            textNodeCount = 0;
        }

    }

    public static void concatenate(Compiler compiler, Node.Nodes page)
            throws JasperException {

        TextCatVisitor v = new TextCatVisitor(compiler);
        page.visit(v);

        // 清理, 如果页面以模板文本结束
        v.collectText();
    }
}
