package eluajs.ui;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Dependency-free, crisp vector icon pack for the eLuajs shell.
 * Everything is painted with Java2D so the app stays lightweight.
 */
public final class Icons {
    private Icons() {}

    public static Icon icon(String name, Color color, int size) {
        return new DrawIcon(name, color, size);
    }

    public static void draw(Graphics2D g, String name, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create(x, y, size, size);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        float s = size;
        g2.setStroke(new BasicStroke(Math.max(1.35f, s / 13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "files" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .27f, s * .12f);
                p.lineTo(s * .60f, s * .12f);
                p.lineTo(s * .78f, s * .30f);
                p.lineTo(s * .78f, s * .88f);
                p.lineTo(s * .27f, s * .88f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .60f, s * .12f, s * .60f, s * .30f));
                g2.draw(new Line2D.Float(s * .60f, s * .30f, s * .78f, s * .30f));
                g2.draw(new Line2D.Float(s * .38f, s * .47f, s * .67f, s * .47f));
                g2.draw(new Line2D.Float(s * .38f, s * .60f, s * .67f, s * .60f));
                g2.draw(new Line2D.Float(s * .38f, s * .73f, s * .57f, s * .73f));
            }
            case "projects" -> {
                g2.draw(new RoundRectangle2D.Float(s * .14f, s * .14f, s * .30f, s * .30f, s * .08f, s * .08f));
                g2.draw(new RoundRectangle2D.Float(s * .56f, s * .14f, s * .30f, s * .30f, s * .08f, s * .08f));
                g2.draw(new RoundRectangle2D.Float(s * .14f, s * .56f, s * .30f, s * .30f, s * .08f, s * .08f));
                g2.draw(new RoundRectangle2D.Float(s * .56f, s * .56f, s * .30f, s * .30f, s * .08f, s * .08f));
            }
            case "outline" -> {
                g2.fill(new Ellipse2D.Float(s * .15f, s * .20f, s * .11f, s * .11f));
                g2.draw(new Line2D.Float(s * .34f, s * .255f, s * .84f, s * .255f));
                g2.draw(new Line2D.Float(s * .26f, s * .48f, s * .26f, s * .74f));
                g2.draw(new Line2D.Float(s * .26f, s * .50f, s * .36f, s * .50f));
                g2.draw(new Line2D.Float(s * .44f, s * .50f, s * .84f, s * .50f));
                g2.draw(new Line2D.Float(s * .26f, s * .74f, s * .36f, s * .74f));
                g2.draw(new Line2D.Float(s * .44f, s * .74f, s * .74f, s * .74f));
            }
            case "pages" -> {
                RoundRectangle2D back = new RoundRectangle2D.Float(s * .20f, s * .18f, s * .46f, s * .56f, s * .10f, s * .10f);
                RoundRectangle2D front = new RoundRectangle2D.Float(s * .34f, s * .28f, s * .46f, s * .56f, s * .10f, s * .10f);
                g2.draw(back);
                g2.draw(front);
            }
            case "file-plus" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .14f);
                p.lineTo(s * .54f, s * .14f);
                p.lineTo(s * .72f, s * .32f);
                p.lineTo(s * .72f, s * .54f);
                p.moveTo(s * .52f, s * .86f);
                p.lineTo(s * .22f, s * .86f);
                p.lineTo(s * .22f, s * .14f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .54f, s * .14f, s * .54f, s * .32f));
                g2.draw(new Line2D.Float(s * .54f, s * .32f, s * .72f, s * .32f));
                g2.draw(new Line2D.Float(s * .72f, s * .62f, s * .72f, s * .88f));
                g2.draw(new Line2D.Float(s * .59f, s * .75f, s * .85f, s * .75f));
            }
            case "folder-plus" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .14f, s * .26f);
                p.lineTo(s * .42f, s * .26f);
                p.lineTo(s * .50f, s * .36f);
                p.lineTo(s * .84f, s * .36f);
                p.lineTo(s * .84f, s * .54f);
                p.moveTo(s * .54f, s * .80f);
                p.lineTo(s * .14f, s * .80f);
                p.lineTo(s * .14f, s * .26f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .74f, s * .60f, s * .74f, s * .86f));
                g2.draw(new Line2D.Float(s * .61f, s * .73f, s * .87f, s * .73f));
            }
            case "folder-open" -> {
                Path2D.Float back = new Path2D.Float();
                back.moveTo(s * .14f, s * .26f);
                back.lineTo(s * .42f, s * .26f);
                back.lineTo(s * .50f, s * .36f);
                back.lineTo(s * .82f, s * .36f);
                back.lineTo(s * .82f, s * .48f);
                back.moveTo(s * .14f, s * .26f);
                back.lineTo(s * .14f, s * .80f);
                g2.draw(back);
                Path2D.Float front = new Path2D.Float();
                front.moveTo(s * .14f, s * .80f);
                front.lineTo(s * .26f, s * .48f);
                front.lineTo(s * .90f, s * .48f);
                front.lineTo(s * .78f, s * .80f);
                front.closePath();
                g2.draw(front);
            }
            case "page-plus" -> {
                g2.draw(new RoundRectangle2D.Float(s * .22f, s * .14f, s * .50f, s * .72f, s * .10f, s * .10f));
                g2.draw(new Line2D.Float(s * .40f, s * .26f, s * .54f, s * .26f));
                g2.draw(new Line2D.Float(s * .47f, s * .42f, s * .47f, s * .66f));
                g2.draw(new Line2D.Float(s * .35f, s * .54f, s * .59f, s * .54f));
            }
            case "page-minus" -> {
                g2.draw(new RoundRectangle2D.Float(s * .22f, s * .14f, s * .50f, s * .72f, s * .10f, s * .10f));
                g2.draw(new Line2D.Float(s * .40f, s * .26f, s * .54f, s * .26f));
                g2.draw(new Line2D.Float(s * .35f, s * .54f, s * .59f, s * .54f));
            }
            case "class" -> {
                Path2D.Float l = new Path2D.Float();
                l.moveTo(s * .36f, s * .20f);
                l.lineTo(s * .22f, s * .20f);
                l.lineTo(s * .22f, s * .44f);
                l.lineTo(s * .14f, s * .50f);
                l.lineTo(s * .22f, s * .56f);
                l.lineTo(s * .22f, s * .80f);
                l.lineTo(s * .36f, s * .80f);
                g2.draw(l);
                Path2D.Float r = new Path2D.Float();
                r.moveTo(s * .64f, s * .20f);
                r.lineTo(s * .78f, s * .20f);
                r.lineTo(s * .78f, s * .44f);
                r.lineTo(s * .86f, s * .50f);
                r.lineTo(s * .78f, s * .56f);
                r.lineTo(s * .78f, s * .80f);
                r.lineTo(s * .64f, s * .80f);
                g2.draw(r);
                g2.fill(new Ellipse2D.Float(s * .44f, s * .44f, s * .12f, s * .12f));
            }
            case "method" -> {
                Path2D.Float hex = new Path2D.Float();
                hex.moveTo(s * .50f, s * .14f);
                hex.lineTo(s * .82f, s * .32f);
                hex.lineTo(s * .82f, s * .68f);
                hex.lineTo(s * .50f, s * .86f);
                hex.lineTo(s * .18f, s * .68f);
                hex.lineTo(s * .18f, s * .32f);
                hex.closePath();
                g2.draw(hex);
                g2.draw(new Line2D.Float(s * .50f, s * .50f, s * .82f, s * .32f));
                g2.draw(new Line2D.Float(s * .50f, s * .50f, s * .18f, s * .32f));
                g2.draw(new Line2D.Float(s * .50f, s * .50f, s * .50f, s * .86f));
            }
            case "tag" -> {
                g2.draw(new Line2D.Float(s * .36f, s * .18f, s * .28f, s * .82f));
                g2.draw(new Line2D.Float(s * .72f, s * .18f, s * .64f, s * .82f));
                g2.draw(new Line2D.Float(s * .16f, s * .38f, s * .84f, s * .38f));
                g2.draw(new Line2D.Float(s * .14f, s * .64f, s * .82f, s * .64f));
            }
            case "code" -> {
                Path2D.Float l = new Path2D.Float();
                l.moveTo(s * .36f, s * .26f);
                l.lineTo(s * .14f, s * .50f);
                l.lineTo(s * .36f, s * .74f);
                g2.draw(l);
                Path2D.Float r = new Path2D.Float();
                r.moveTo(s * .64f, s * .26f);
                r.lineTo(s * .86f, s * .50f);
                r.lineTo(s * .64f, s * .74f);
                g2.draw(r);
                g2.draw(new Line2D.Float(s * .56f, s * .22f, s * .44f, s * .78f));
            }
            case "search" -> {
                g2.draw(new Ellipse2D.Float(s * .20f, s * .18f, s * .48f, s * .48f));
                g2.draw(new Line2D.Float(s * .60f, s * .60f, s * .82f, s * .82f));
            }
            case "assets" -> {
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .20f, s * .68f, s * .60f, s * .10f, s * .10f));
                g2.draw(new Ellipse2D.Float(s * .28f, s * .30f, s * .13f, s * .13f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .72f);
                p.lineTo(s * .42f, s * .50f);
                p.lineTo(s * .56f, s * .64f);
                p.lineTo(s * .66f, s * .54f);
                p.lineTo(s * .78f, s * .70f);
                g2.draw(p);
            }
            case "library", "package" -> {
                Path2D.Float box = new Path2D.Float();
                box.moveTo(s * .50f, s * .14f);
                box.lineTo(s * .84f, s * .30f);
                box.lineTo(s * .84f, s * .70f);
                box.lineTo(s * .50f, s * .86f);
                box.lineTo(s * .16f, s * .70f);
                box.lineTo(s * .16f, s * .30f);
                box.closePath();
                g2.draw(box);
                g2.draw(new Line2D.Float(s * .16f, s * .30f, s * .50f, s * .46f));
                g2.draw(new Line2D.Float(s * .84f, s * .30f, s * .50f, s * .46f));
                g2.draw(new Line2D.Float(s * .50f, s * .46f, s * .50f, s * .86f));
                g2.draw(new Line2D.Float(s * .33f, s * .22f, s * .67f, s * .38f));
            }
            case "permissions" -> {
                g2.draw(new RoundRectangle2D.Float(s * .25f, s * .45f, s * .50f, s * .42f, s * .10f, s * .10f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .34f, s * .45f);
                p.lineTo(s * .34f, s * .33f);
                p.curveTo(s * .34f, s * .16f, s * .66f, s * .16f, s * .66f, s * .33f);
                p.lineTo(s * .66f, s * .45f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .50f, s * .58f, s * .50f, s * .72f));
            }
            case "shield" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .50f, s * .13f);
                p.lineTo(s * .82f, s * .26f);
                p.lineTo(s * .82f, s * .50f);
                p.curveTo(s * .82f, s * .70f, s * .66f, s * .82f, s * .50f, s * .88f);
                p.curveTo(s * .34f, s * .82f, s * .18f, s * .70f, s * .18f, s * .50f);
                p.lineTo(s * .18f, s * .26f);
                p.closePath();
                g2.draw(p);
                Path2D.Float ck = new Path2D.Float();
                ck.moveTo(s * .36f, s * .51f);
                ck.lineTo(s * .46f, s * .61f);
                ck.lineTo(s * .66f, s * .40f);
                g2.draw(ck);
            }
            case "heart" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .12f, s * .52f);
                p.lineTo(s * .30f, s * .52f);
                p.lineTo(s * .40f, s * .24f);
                p.lineTo(s * .54f, s * .76f);
                p.lineTo(s * .66f, s * .42f);
                p.lineTo(s * .74f, s * .52f);
                p.lineTo(s * .88f, s * .52f);
                g2.draw(p);
            }
            case "checkpoint" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .26f, s * .16f);
                p.lineTo(s * .74f, s * .16f);
                p.lineTo(s * .74f, s * .84f);
                p.lineTo(s * .50f, s * .68f);
                p.lineTo(s * .26f, s * .84f);
                p.closePath();
                g2.draw(p);
            }
            case "accessibility" -> {
                g2.draw(new Ellipse2D.Float(s * .14f, s * .14f, s * .72f, s * .72f));
                g2.fill(new Ellipse2D.Float(s * .44f, s * .26f, s * .12f, s * .12f));
                g2.draw(new Line2D.Float(s * .30f, s * .46f, s * .70f, s * .46f));
                g2.draw(new Line2D.Float(s * .50f, s * .46f, s * .50f, s * .62f));
                g2.draw(new Line2D.Float(s * .50f, s * .62f, s * .38f, s * .76f));
                g2.draw(new Line2D.Float(s * .50f, s * .62f, s * .62f, s * .76f));
            }
            case "notes" -> {
                g2.draw(new RoundRectangle2D.Float(s * .20f, s * .14f, s * .60f, s * .72f, s * .10f, s * .10f));
                g2.draw(new Line2D.Float(s * .34f, s * .34f, s * .66f, s * .34f));
                g2.draw(new Line2D.Float(s * .34f, s * .50f, s * .66f, s * .50f));
                g2.draw(new Line2D.Float(s * .34f, s * .66f, s * .54f, s * .66f));
            }
            case "link" -> {
                g2.draw(new Ellipse2D.Float(s * .16f, s * .18f, s * .22f, s * .22f));
                g2.draw(new Ellipse2D.Float(s * .62f, s * .18f, s * .22f, s * .22f));
                g2.draw(new Ellipse2D.Float(s * .39f, s * .62f, s * .22f, s * .22f));
                g2.draw(new Line2D.Float(s * .38f, s * .29f, s * .62f, s * .29f));
                g2.draw(new Line2D.Float(s * .31f, s * .39f, s * .45f, s * .62f));
                g2.draw(new Line2D.Float(s * .69f, s * .39f, s * .55f, s * .62f));
            }
            case "globe" -> {
                g2.draw(new Ellipse2D.Float(s * .15f, s * .15f, s * .70f, s * .70f));
                g2.draw(new Ellipse2D.Float(s * .33f, s * .15f, s * .34f, s * .70f));
                g2.draw(new Line2D.Float(s * .16f, s * .50f, s * .84f, s * .50f));
            }
            case "device" -> {
                g2.draw(new RoundRectangle2D.Float(s * .27f, s * .12f, s * .46f, s * .76f, s * .10f, s * .10f));
                g2.draw(new Line2D.Float(s * .44f, s * .76f, s * .56f, s * .76f));
            }
            case "devices" -> {
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .22f, s * .40f, s * .64f, s * .08f, s * .08f));
                g2.draw(new RoundRectangle2D.Float(s * .44f, s * .14f, s * .40f, s * .64f, s * .08f, s * .08f));
                g2.draw(new Line2D.Float(s * .60f, s * .68f, s * .68f, s * .68f));
            }
            case "diff" -> {
                g2.draw(new RoundRectangle2D.Float(s * .14f, s * .18f, s * .72f, s * .64f, s * .10f, s * .10f));
                g2.draw(new Line2D.Float(s * .50f, s * .18f, s * .50f, s * .82f));
                g2.draw(new Line2D.Float(s * .25f, s * .50f, s * .41f, s * .50f));
                g2.draw(new Line2D.Float(s * .33f, s * .42f, s * .33f, s * .58f));
                g2.draw(new Line2D.Float(s * .59f, s * .50f, s * .75f, s * .50f));
            }
            case "release" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .18f, s * .50f);
                p.lineTo(s * .52f, s * .16f);
                p.lineTo(s * .84f, s * .16f);
                p.lineTo(s * .84f, s * .48f);
                p.lineTo(s * .50f, s * .82f);
                p.closePath();
                g2.draw(p);
                g2.fill(new Ellipse2D.Float(s * .64f, s * .28f, s * .10f, s * .10f));
            }
            case "focus" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .18f, s * .36f); p.lineTo(s * .18f, s * .18f); p.lineTo(s * .36f, s * .18f);
                p.moveTo(s * .64f, s * .18f); p.lineTo(s * .82f, s * .18f); p.lineTo(s * .82f, s * .36f);
                p.moveTo(s * .82f, s * .64f); p.lineTo(s * .82f, s * .82f); p.lineTo(s * .64f, s * .82f);
                p.moveTo(s * .36f, s * .82f); p.lineTo(s * .18f, s * .82f); p.lineTo(s * .18f, s * .64f);
                g2.draw(p);
                g2.fill(new Ellipse2D.Float(s * .44f, s * .44f, s * .12f, s * .12f));
            }
            case "update" -> {
                g2.draw(new Ellipse2D.Float(s * .16f, s * .16f, s * .68f, s * .68f));
                g2.draw(new Line2D.Float(s * .50f, s * .68f, s * .50f, s * .34f));
                g2.draw(new Line2D.Float(s * .35f, s * .48f, s * .50f, s * .33f));
                g2.draw(new Line2D.Float(s * .65f, s * .48f, s * .50f, s * .33f));
            }
            case "pin" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .56f, s * .16f);
                p.lineTo(s * .84f, s * .44f);
                p.lineTo(s * .66f, s * .52f);
                p.lineTo(s * .52f, s * .66f);
                p.lineTo(s * .34f, s * .48f);
                p.lineTo(s * .48f, s * .34f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .42f, s * .58f, s * .18f, s * .82f));
            }
            case "chat-plus" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .20f);
                p.lineTo(s * .80f, s * .20f);
                p.lineTo(s * .80f, s * .68f);
                p.lineTo(s * .44f, s * .68f);
                p.lineTo(s * .24f, s * .84f);
                p.lineTo(s * .24f, s * .68f);
                p.lineTo(s * .20f, s * .68f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .50f, s * .32f, s * .50f, s * .56f));
                g2.draw(new Line2D.Float(s * .38f, s * .44f, s * .62f, s * .44f));
            }
            case "stop" -> {
                g2.draw(new Ellipse2D.Float(s * .14f, s * .14f, s * .72f, s * .72f));
                g2.fill(new RoundRectangle2D.Float(s * .36f, s * .36f, s * .28f, s * .28f, s * .06f, s * .06f));
            }
            case "bolt" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .56f, s * .12f);
                p.lineTo(s * .22f, s * .54f);
                p.lineTo(s * .48f, s * .54f);
                p.lineTo(s * .42f, s * .88f);
                p.lineTo(s * .78f, s * .46f);
                p.lineTo(s * .52f, s * .46f);
                p.closePath();
                g2.draw(p);
            }
            case "eye" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .12f, s * .50f);
                p.curveTo(s * .26f, s * .24f, s * .74f, s * .24f, s * .88f, s * .50f);
                p.curveTo(s * .74f, s * .76f, s * .26f, s * .76f, s * .12f, s * .50f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Ellipse2D.Float(s * .38f, s * .38f, s * .24f, s * .24f));
            }
            case "edit" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .80f);
                p.lineTo(s * .26f, s * .58f);
                p.lineTo(s * .68f, s * .16f);
                p.lineTo(s * .84f, s * .32f);
                p.lineTo(s * .42f, s * .74f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .58f, s * .26f, s * .74f, s * .42f));
            }
            case "wrench" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .78f);
                p.lineTo(s * .54f, s * .46f);
                g2.draw(p);
                g2.draw(new Arc2D.Float(s * .46f, s * .16f, s * .36f, s * .36f, 45, 250, Arc2D.OPEN));
                g2.fill(new Ellipse2D.Float(s * .17f, s * .72f, s * .12f, s * .12f));
            }
            case "alert" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .50f, s * .16f);
                p.lineTo(s * .86f, s * .82f);
                p.lineTo(s * .14f, s * .82f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .50f, s * .40f, s * .50f, s * .60f));
                g2.fill(new Ellipse2D.Float(s * .465f, s * .68f, s * .07f, s * .07f));
            }
            case "trash" -> {
                g2.draw(new Line2D.Float(s * .18f, s * .28f, s * .82f, s * .28f));
                g2.draw(new Line2D.Float(s * .38f, s * .16f, s * .62f, s * .16f));
                g2.draw(new Line2D.Float(s * .38f, s * .16f, s * .36f, s * .28f));
                g2.draw(new Line2D.Float(s * .62f, s * .16f, s * .64f, s * .28f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .26f, s * .28f);
                p.lineTo(s * .30f, s * .84f);
                p.lineTo(s * .70f, s * .84f);
                p.lineTo(s * .74f, s * .28f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .42f, s * .42f, s * .42f, s * .70f));
                g2.draw(new Line2D.Float(s * .58f, s * .42f, s * .58f, s * .70f));
            }
            case "star", "star-filled" -> {
                Path2D.Float star = new Path2D.Float();
                for (int i = 0; i < 10; i++) {
                    double r = (i % 2 == 0) ? s * .38f : s * .17f;
                    double angle = Math.toRadians(-90 + i * 36);
                    float px = (float) (s * .50f + Math.cos(angle) * r);
                    float py = (float) (s * .52f + Math.sin(angle) * r);
                    if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.closePath();
                if ("star-filled".equals(name)) g2.fill(star);
                g2.draw(star);
            }
            case "arrow-right" -> {
                g2.draw(new Line2D.Float(s * .20f, s * .50f, s * .78f, s * .50f));
                g2.draw(new Line2D.Float(s * .56f, s * .28f, s * .78f, s * .50f));
                g2.draw(new Line2D.Float(s * .56f, s * .72f, s * .78f, s * .50f));
            }
            case "arrow-left" -> {
                g2.draw(new Line2D.Float(s * .80f, s * .50f, s * .22f, s * .50f));
                g2.draw(new Line2D.Float(s * .44f, s * .28f, s * .22f, s * .50f));
                g2.draw(new Line2D.Float(s * .44f, s * .72f, s * .22f, s * .50f));
            }
            case "home" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .14f, s * .48f);
                p.lineTo(s * .50f, s * .16f);
                p.lineTo(s * .86f, s * .48f);
                p.lineTo(s * .78f, s * .48f);
                p.lineTo(s * .78f, s * .84f);
                p.lineTo(s * .58f, s * .84f);
                p.lineTo(s * .58f, s * .62f);
                p.lineTo(s * .42f, s * .62f);
                p.lineTo(s * .42f, s * .84f);
                p.lineTo(s * .22f, s * .84f);
                p.lineTo(s * .22f, s * .48f);
                p.closePath();
                g2.draw(p);
            }
            case "folder" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .14f, s * .30f);
                p.lineTo(s * .42f, s * .30f);
                p.lineTo(s * .50f, s * .40f);
                p.lineTo(s * .86f, s * .40f);
                p.lineTo(s * .86f, s * .82f);
                p.lineTo(s * .14f, s * .82f);
                p.closePath();
                g2.draw(p);
            }
            case "plus" -> {
                g2.draw(new Line2D.Float(s * .50f, s * .22f, s * .50f, s * .78f));
                g2.draw(new Line2D.Float(s * .22f, s * .50f, s * .78f, s * .50f));
            }
            case "refresh" -> {
                g2.draw(new Arc2D.Float(s * .20f, s * .20f, s * .60f, s * .60f, 40, 280, Arc2D.OPEN));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .79f, s * .22f);
                p.lineTo(s * .82f, s * .42f);
                p.lineTo(s * .62f, s * .39f);
                g2.draw(p);
            }
            case "copy" -> {
                g2.draw(new RoundRectangle2D.Float(s * .24f, s * .28f, s * .42f, s * .50f, s * .06f, s * .06f));
                g2.draw(new RoundRectangle2D.Float(s * .38f, s * .16f, s * .36f, s * .42f, s * .06f, s * .06f));
            }
            case "settings" -> {
                g2.draw(new Ellipse2D.Float(s * .32f, s * .32f, s * .36f, s * .36f));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float x1 = (float) (s * .50f + Math.cos(a) * s * .26f);
                    float y1 = (float) (s * .50f + Math.sin(a) * s * .26f);
                    float x2 = (float) (s * .50f + Math.cos(a) * s * .39f);
                    float y2 = (float) (s * .50f + Math.sin(a) * s * .39f);
                    g2.draw(new Line2D.Float(x1, y1, x2, y2));
                }
            }
            case "ai" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .52f, s * .10f);
                p.lineTo(s * .61f, s * .38f);
                p.lineTo(s * .90f, s * .47f);
                p.lineTo(s * .61f, s * .56f);
                p.lineTo(s * .52f, s * .86f);
                p.lineTo(s * .43f, s * .56f);
                p.lineTo(s * .14f, s * .47f);
                p.lineTo(s * .43f, s * .38f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .76f, s * .72f, s * .76f, s * .88f));
                g2.draw(new Line2D.Float(s * .68f, s * .80f, s * .84f, s * .80f));
            }
            case "about" -> {
                g2.draw(new Ellipse2D.Float(s * .14f, s * .14f, s * .72f, s * .72f));
                g2.draw(new Line2D.Float(s * .50f, s * .43f, s * .50f, s * .70f));
                g2.fill(new Ellipse2D.Float(s * .47f, s * .27f, s * .06f, s * .06f));
            }
            case "history" -> {
                g2.draw(new Ellipse2D.Float(s * .18f, s * .18f, s * .64f, s * .64f));
                g2.draw(new Line2D.Float(s * .50f, s * .32f, s * .50f, s * .52f));
                g2.draw(new Line2D.Float(s * .50f, s * .52f, s * .66f, s * .60f));
            }
            case "undo" -> {
                Path2D.Float arrow = new Path2D.Float();
                arrow.moveTo(s * .36f, s * .22f);
                arrow.lineTo(s * .16f, s * .40f);
                arrow.lineTo(s * .36f, s * .58f);
                g2.draw(arrow);
                Path2D.Float curve = new Path2D.Float();
                curve.moveTo(s * .18f, s * .40f);
                curve.lineTo(s * .62f, s * .40f);
                curve.curveTo(s * .84f, s * .40f, s * .84f, s * .78f, s * .58f, s * .78f);
                curve.lineTo(s * .30f, s * .78f);
                g2.draw(curve);
            }
            case "user" -> {
                g2.draw(new Ellipse2D.Float(s * .36f, s * .18f, s * .28f, s * .28f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .84f);
                p.curveTo(s * .22f, s * .58f, s * .78f, s * .58f, s * .80f, s * .84f);
                g2.draw(p);
            }
            case "send" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .16f, s * .50f);
                p.lineTo(s * .84f, s * .22f);
                p.lineTo(s * .62f, s * .80f);
                p.lineTo(s * .48f, s * .58f);
                p.closePath();
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .48f, s * .58f, s * .84f, s * .22f));
            }
            case "check" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .20f, s * .54f);
                p.lineTo(s * .42f, s * .76f);
                p.lineTo(s * .82f, s * .26f);
                g2.draw(p);
            }
            case "minimize" -> g2.draw(new Line2D.Float(s * .24f, s * .50f, s * .76f, s * .50f));
            case "maximize" -> g2.draw(new RoundRectangle2D.Float(s * .24f, s * .24f, s * .52f, s * .52f, s * .06f, s * .06f));
            case "restore" -> {
                g2.draw(new RoundRectangle2D.Float(s * .18f, s * .30f, s * .46f, s * .42f, s * .05f, s * .05f));
                g2.draw(new RoundRectangle2D.Float(s * .36f, s * .18f, s * .46f, s * .42f, s * .05f, s * .05f));
            }
            case "close" -> {
                g2.draw(new Line2D.Float(s * .28f, s * .28f, s * .72f, s * .72f));
                g2.draw(new Line2D.Float(s * .72f, s * .28f, s * .28f, s * .72f));
            }
            case "run" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .32f, s * .20f);
                p.lineTo(s * .78f, s * .50f);
                p.lineTo(s * .32f, s * .80f);
                p.closePath();
                g2.draw(p);
            }
            case "import" -> {
                g2.draw(new Line2D.Float(s * .50f, s * .20f, s * .50f, s * .62f));
                g2.draw(new Line2D.Float(s * .34f, s * .48f, s * .50f, s * .64f));
                g2.draw(new Line2D.Float(s * .66f, s * .48f, s * .50f, s * .64f));
                g2.draw(new Line2D.Float(s * .26f, s * .76f, s * .74f, s * .76f));
                g2.draw(new Line2D.Float(s * .26f, s * .76f, s * .26f, s * .66f));
                g2.draw(new Line2D.Float(s * .74f, s * .76f, s * .74f, s * .66f));
            }
            case "tune" -> {
                g2.draw(new Line2D.Float(s * .18f, s * .30f, s * .82f, s * .30f));
                g2.draw(new Line2D.Float(s * .18f, s * .52f, s * .82f, s * .52f));
                g2.draw(new Line2D.Float(s * .18f, s * .74f, s * .82f, s * .74f));
                g2.fill(new Ellipse2D.Float(s * .34f, s * .22f, s * .16f, s * .16f));
                g2.fill(new Ellipse2D.Float(s * .58f, s * .44f, s * .16f, s * .16f));
                g2.fill(new Ellipse2D.Float(s * .42f, s * .66f, s * .16f, s * .16f));
            }
            case "git" -> {
                g2.draw(new Line2D.Float(s * .33f, s * .24f, s * .33f, s * .76f));
                g2.draw(new Line2D.Float(s * .33f, s * .48f, s * .67f, s * .34f));
                g2.draw(new Ellipse2D.Float(s * .24f, s * .14f, s * .18f, s * .18f));
                g2.draw(new Ellipse2D.Float(s * .24f, s * .68f, s * .18f, s * .18f));
                g2.draw(new Ellipse2D.Float(s * .58f, s * .24f, s * .18f, s * .18f));
            }
            case "split" -> {
                g2.draw(new Line2D.Float(s * .50f, s * .18f, s * .50f, s * .82f));
                g2.draw(new RoundRectangle2D.Float(s * .16f, s * .22f, s * .26f, s * .56f, s * .06f, s * .06f));
                g2.draw(new RoundRectangle2D.Float(s * .58f, s * .22f, s * .26f, s * .56f, s * .06f, s * .06f));
            }
            case "save" -> {
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .22f, s * .16f);
                p.lineTo(s * .70f, s * .16f);
                p.lineTo(s * .82f, s * .28f);
                p.lineTo(s * .82f, s * .84f);
                p.lineTo(s * .22f, s * .84f);
                p.closePath();
                g2.draw(p);
                g2.draw(new RoundRectangle2D.Float(s * .34f, s * .52f, s * .34f, s * .24f, s * .04f, s * .04f));
            }
            case "terminal" -> {
                g2.draw(new RoundRectangle2D.Float(s * .12f, s * .20f, s * .76f, s * .60f, s * .10f, s * .10f));
                Path2D.Float p = new Path2D.Float();
                p.moveTo(s * .28f, s * .40f);
                p.lineTo(s * .40f, s * .50f);
                p.lineTo(s * .28f, s * .60f);
                g2.draw(p);
                g2.draw(new Line2D.Float(s * .48f, s * .60f, s * .66f, s * .60f));
            }
            default -> g2.draw(new Ellipse2D.Float(s * .26f, s * .26f, s * .48f, s * .48f));
        }
        g2.dispose();
    }

    public static Image projectIcon(String appName, String packageName, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int hash = (appName + "|" + packageName).hashCode();
        float hue = ((hash & 0x7fffffff) % 360) / 360f;
        Color a = Color.getHSBColor(hue, 0.52f, 0.86f);
        Color b = Color.getHSBColor((hue + 0.04f) % 1f, 0.72f, 0.52f);
        g.setPaint(new GradientPaint(0, 0, a, size, size, b));
        int pad = Math.max(1, size / 16);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, Math.max(6, size / 4), Math.max(6, size / 4));
        g.setColor(new Color(255, 255, 255, 32));
        g.fillRoundRect(size / 8, size / 10, size - size / 4, Math.max(2, size / 10), size / 8, size / 8);
        String initial = initial(appName);
        int fontSize = Math.max(9, (int) (size * 0.48f));
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int tx = (size - fm.stringWidth(initial)) / 2;
        int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.setColor(new Color(8, 22, 20, 190));
        g.drawString(initial, tx + Math.max(1, size / 48), ty + Math.max(1, size / 48));
        g.setColor(new Color(10, 32, 28));
        g.drawString(initial, tx, ty);
        g.dispose();
        return image;
    }

    public static Icon fileIcon(Path path, boolean directory, boolean expanded) {
        String name = path == null || path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (directory) {
            Color folderColor = switch (name) {
                case "java", "kotlin", "src" -> new Color(110, 185, 238);
                case "res", "layout", "drawable", "values" -> new Color(186, 142, 232);
                case "assets" -> new Color(242, 178, 92);
                case "gradle", ".gradle" -> new Color(96, 202, 138);
                default -> new Color(226, 184, 107);
            };
            return icon(expanded ? "folder-open" : "folder", folderColor, 16);
        }
        String type;
        Color color;
        if (name.equals("androidmanifest.xml")) { type = "M"; color = new Color(55, 204, 156); }
        else if (name.endsWith(".java")) { type = "J"; color = new Color(224, 108, 117); }
        else if (name.endsWith(".kt")) { type = "K"; color = new Color(170, 130, 240); }
        else if (name.endsWith(".xml")) { type = "X"; color = new Color(86, 156, 214); }
        else if (name.endsWith(".gradle") || name.endsWith(".kts")) { type = "G"; color = new Color(87, 186, 120); }
        else if (name.endsWith(".properties")) { type = "#"; color = new Color(142, 158, 178); }
        else if (name.endsWith(".json")) { type = "{}"; color = new Color(222, 188, 86); }
        else if (name.endsWith(".md") || name.endsWith(".txt")) { type = "T"; color = new Color(108, 198, 214); }
        else if (name.endsWith(".sh") || name.endsWith(".bat")) { type = "$_"; color = new Color(118, 206, 146); }
        else if (name.endsWith(".apk") || name.endsWith(".aab")) { return icon("package", new Color(55, 214, 164), 16); }
        else if (name.matches(".*\\.(png|jpg|jpeg|webp|gif|svg|ico)$")) { return icon("assets", new Color(188, 136, 226), 16); }
        else if (name.equals(".gitignore") || name.endsWith(".pro")) { return icon("settings", new Color(138, 152, 168), 16); }
        else { return icon("files", new Color(130, 146, 164), 16); }
        return new TreeIcon(type, color, 16);
    }

    private static String initial(String text) {
        if (text == null || text.isBlank()) return "A";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) return String.valueOf(Character.toUpperCase(c));
        }
        return "A";
    }

    private static final class DrawIcon implements Icon {
        private final String name;
        private final Color color;
        private final int size;
        DrawIcon(String name, Color color, int size) { this.name = name; this.color = color; this.size = size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) { draw((Graphics2D) g, name, x, y, size, color); }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    private static final class TreeIcon implements Icon {
        private final String glyph;
        private final Color color;
        private final int size;
        TreeIcon(String glyph, Color color, int size) { this.glyph = glyph; this.color = color; this.size = size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create(x, y, size, size);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 42));
            g2.fillRoundRect(1, 1, size - 2, size - 2, 5, 5);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
            g2.drawRoundRect(1, 1, size - 3, size - 3, 5, 5);
            g2.setColor(color);
            int fs = glyph.length() > 1 ? 7 : 9;
            g2.setFont(new Font("SansSerif", Font.BOLD, fs));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (size - fm.stringWidth(glyph)) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(glyph, tx, ty);
            g2.dispose();
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
