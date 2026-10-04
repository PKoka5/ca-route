package com.caroute.ui;

import java.awt.Dimension;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.View;

/** BoxLayout must measure wrapped height at the actual sidebar width. */
final class WrappedText extends JTextArea
{
    WrappedText(String text) { super(text); setLineWrap(true); setWrapStyleWord(true); }
    @Override public void setBounds(int x, int y, int width, int height)
    {
        boolean changed = width != getWidth();
        super.setBounds(x, y, width, height);
        if (changed && getParent() != null)
        {
            // A narrower allocation changes the line count. BoxLayout caches
            // child measurements, including in collapsed section ancestors.
            for (Container parent = getParent(); parent != null; parent = parent.getParent()) parent.invalidate();
            revalidate();
        }
    }
    @Override public Dimension getPreferredSize()
    {
        int width = 210;
        if (getParent() != null && getParent().getWidth() > 20)
        {
            Insets parentInsets = getParent().getInsets();
            width = getParent().getWidth() - parentInsets.left - parentInsets.right;
        }
        width = Math.max(40, width);
        Insets insets = getInsets();
        View text = getUI().getRootView(this);
        // Measure the same content width the layout will allocate. Measuring via
        // setSize/getPreferredSize can reuse the previous wrapped line count.
        text.setSize(Math.max(1, width - insets.left - insets.right), Short.MAX_VALUE);
        int height = (int) Math.ceil(text.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        try
        {
            // WrappedPlainView can under-report its cached preferred span until
            // the final line is laid out. Include the actual last character.
            Shape end = text.modelToView(Math.max(0, getDocument().getLength() - 1),
                new Rectangle(insets.left, insets.top, Math.max(1, width - insets.left - insets.right), Short.MAX_VALUE),
                Position.Bias.Forward);
            if (end != null) height = Math.max(height, (int) Math.ceil(end.getBounds2D().getMaxY()) + insets.bottom);
        }
        catch (BadLocationException ex) { throw new IllegalStateException("Cannot measure wrapped text", ex); }
        return new Dimension(width, height);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    @Override public Dimension getMinimumSize() { return new Dimension(30, getPreferredSize().height); }
}
