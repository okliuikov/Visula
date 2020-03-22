package com.serena.eclipse.ui;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Table;
// dimensionscac://stl-qa-vcw8ee64/cm_typical@dim14/QLARIUS/JAVA_BRANCHA_STR/
// dimensionscac://stl-qa-vcw8ee64/cm_typical@dim14/qlarius/java_brancha_str

// dimensions://stl-ta-vcw8-11/cm_typical@dim10/qlarius/java_brancha_str
// dimensions://orl-dev-cm64prd/cmprod@dmprod/dmprod/CM_GIT_PLUGIN_pasilla
//dimensions://stl-ta-vcrh6-3/cm_typical@dim14/qlarius/topic1
public class SortImageHelper implements DisposeListener {

    private Image imageDescending;
    private Image imageAscending;
    private Table table;

    public SortImageHelper(Table table) {
        this.table = table;
    }

    public Image getImageAscending() {
        if (imageAscending == null) {
            createImages();
        }
        return imageAscending;
    }

    public Image getImageDescending() {
        if (imageDescending == null) {
            createImages();
        }
        return imageDescending;
    }

    @Override
    public void widgetDisposed(DisposeEvent e) {
        if (imageDescending != null) {
            imageDescending.dispose();
        }
        if (imageAscending != null) {
            imageAscending.dispose();
        }
    }

    private void createImages() {
        
        int itemHeight = table.getItemHeight();
        Color foreground = table.getForeground();
        Color background = table.getBackground();

        /* \/ - descending */
        PaletteData palette = new PaletteData(new RGB[] { foreground.getRGB(), background.getRGB() });
        ImageData imageData = new ImageData(itemHeight, itemHeight, 4, palette);
        imageData.transparentPixel = 1;
        imageDescending = new Image(table.getDisplay(), imageData);
        GC gc = new GC(imageDescending);
        gc.setBackground(background);
        gc.fillRectangle(0, 0, itemHeight, itemHeight);

        int midPoint = itemHeight / 2;
        int halfWidth = midPoint / 2;
        int halfHeight = halfWidth / 2;
        gc.setBackground(foreground);

        // triangle around midpoint
        gc.fillPolygon(new int[] { midPoint - halfWidth, midPoint - halfHeight, // left vertex
                midPoint + halfWidth, midPoint - halfHeight, // right vertex
                midPoint, midPoint + halfHeight }); // bottom vertex
        gc.dispose();

        /* /\ - ascending */
        palette = new PaletteData(new RGB[] { foreground.getRGB(), background.getRGB() });
        imageData = new ImageData(itemHeight, itemHeight, 4, palette);
        imageData.transparentPixel = 1;
        imageAscending = new Image(table.getDisplay(), imageData);
        gc = new GC(imageAscending);
        gc.setBackground(background);
        gc.fillRectangle(0, 0, itemHeight, itemHeight);
        gc.setBackground(foreground);
        // one more pixel on windows to have both triangles to be the same size
        int adjustment = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0 ? 1 : 0;
        gc.fillPolygon(new int[] { midPoint - halfWidth - adjustment, midPoint + halfHeight, // left vertex
                midPoint, midPoint - halfHeight - 1, // top vertex
                midPoint + halfWidth + adjustment, midPoint + halfHeight }); // right vertex
        gc.dispose();
    }

}
