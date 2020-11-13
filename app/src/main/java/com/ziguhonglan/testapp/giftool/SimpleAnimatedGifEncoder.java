package com.ziguhonglan.testapp.giftool;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Gif编码压缩
 */
public class SimpleAnimatedGifEncoder {

    private int width; // 图片帧的宽度
    private int height; // 图片帧的高度
    private int x = 0;
    private int y = 0;
    private int transparent = -1; // transparent color if given
    private int transIndex; // transparent index in color table
    private int repeat = -1; // 重复设置，0表示无限重复
    private int delay = 0; // frame delay (hundredths)
    private boolean started = false; // ready to output frames
    private OutputStream out;
    private Bitmap image; // 当前帧
    private byte[] pixels; // BGR byte array from frame
    private byte[] indexedPixels; // converted frame indexed to palette
    private int colorDepth; // number of bit planes
    private byte[] colorTab; // RGB palette
    private boolean[] usedEntry = new boolean[256]; // active palette entries
    private int palSize = 7; // color table size (bits-1)
    private int dispose = -1; // disposal code (-1 = use default)
    private boolean closeStream = true; // close stream when finished
    private boolean firstFrame = true;
    private boolean sizeSet = false; // if false, get size from first frame
    private int sample = 10; // default sample interval for quantizer
    private Paint paint = new Paint();

    /**
     * Sets the delay time between each frame, or changes it for subsequent frames
     * (applies to last frame added).
     *
     * @param ms int delay time in milliseconds
     */
    public void setDelay(int ms) {
        delay = ms / 10;
    }

    public void setDelayNotDivide(int ms) {
        delay = ms;
    }

    /**
     * Sets the GIF frame disposal code for the last added frame and any
     * subsequent frames. Default is 0 if no transparent color has been set,
     * otherwise 2.
     *
     * @param code int disposal code.
     */
    public void setDispose(int code) {
        if (code >= 0) {
            dispose = code;
        }
    }

    /**
     * Sets the number of times the set of GIF frames should be played. Default is
     * 1; 0 means play indefinitely. Must be invoked before the first image is
     * added.
     *
     * @param repeat int number of iterations.
     */
    public void setRepeat(int repeat) {
        if (repeat >= 0) {
            this.repeat = repeat;
        }
    }

    /**
     * Sets the transparent color for the last added frame and any subsequent
     * frames. Since all colors are subject to modification in the quantization
     * process, the color in the final palette for each frame closest to the given
     * color becomes the transparent color for that frame. May be set to null to
     * indicate no transparent color.
     *
     * @param c Color to be treated as transparent on display.
     */
    public void setTransparent(int c) {
        transparent = c;
    }

    private void writeHeader() {
        try {
            writeString("GIF89a");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeTrailer() {
        try {
            out.write(0x3b); // gif trailer
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds next GIF frame. The frame is not written immediately, but is actually
     * deferred until the next frame is received so that timing data can be
     * inserted. Invoking <code>finish()</code> flushes all frames. If
     * <code>setSize</code> was not invoked, the size of the first image is used
     * for all subsequent frames.
     *
     * @param im BufferedImage containing frame to write.
     */
    public void writeFrameData(Bitmap im) {
        if ((im == null) || !started) {
            return;
        }
        try {
            image = im;
            if (!sizeSet) {
                // use first frame's size
                setSize(im.getWidth(), im.getHeight());
            }
            getImagePixels(); // convert to correct format if necessary
            analyzePixels(); // build color table & map pixels
            if (firstFrame) {
                writeLSD(); // logical screen descriptior
                writePalette(); // global color table
                if (repeat >= 0) {
                    // use NS app extension to indicate reps
                    writeNetscapeExt();
                }
            }
            writeGraphicCtrlExt(); // write graphic control extension
            writeImageDesc(); // image descriptor
            if (!firstFrame) {
                writePalette(); // local color table
            }
            writePixels(); // encode and write pixel data
            firstFrame = false;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
    }

    /**
     * Flushes any pending data and closes output file. If writing to an
     * OutputStream, the stream is not closed.
     */
    public void finish() {
        if (!started) return;
        started = false;
        try {
            if (closeStream) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (image != null && !image.isRecycled()) {
            image.recycle();
        }
        // reset for subsequent use
        transIndex = 0;
        out = null;
        image = null;
        pixels = null;
        indexedPixels = null;
        colorTab = null;
        closeStream = false;
        firstFrame = true;
    }

    /**
     * Sets frame rate in frames per second. Equivalent to
     * <code>setDelay(1000/fps)</code>.
     *
     * @param fps float frame rate (frames per second)
     */
    public void setFrameRate(float fps) {
        if (fps != 0f) {
            delay = (int) (100 / fps);
        }
    }

    /**
     * Sets quality of color quantization (conversion of images to the maximum 256
     * colors allowed by the GIF specification). Lower values (minimum = 1)
     * produce better colors, but slow processing significantly. 10 is the
     * default, and produces good color mapping at reasonable speeds. Values
     * greater than 20 do not yield significant improvements in speed.
     *
     * @param quality int greater than 0.
     *                经测试，数值越小，压缩时间越久,颜色越清晰
     *                数值越大，压缩时间越快(40M的图片压缩2分钟)
     */
    public void setQuality(int quality) {
        if (quality < 1)
            quality = 1;
        sample = quality;
    }

    /**
     * Sets the GIF frame size. The default size is the size of the first frame
     * added if this method is not invoked.
     *
     * @param w int frame width.
     * @param h int frame width.
     */
    public void setSize(int w, int h) {
        width = w;
        height = h;
        if (width < 1)
            width = 320;
        if (height < 1)
            height = 240;
        sizeSet = true;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Sets the GIF frame position. The position is 0,0 by default.
     * Useful for only updating a section of the image
     *
     * @param x int frame width.
     * @param y int frame width.
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Initiates GIF file creation on the given stream. The stream is not closed
     * automatically.
     *
     * @param os OutputStream on which GIF images are written.
     */
    public void start(OutputStream os, boolean isFirstFrame) {
        if (os == null) return;
        closeStream = false;
        out = os;
        if (isFirstFrame) {
            writeHeader();
        }
        firstFrame = isFirstFrame;
        started = true;
    }

    /**
     * Analyzes image colors and creates color map.
     */
    private void analyzePixels() {
        int len = pixels.length;
        int nPix = len / 3;
        indexedPixels = new byte[nPix];
        NeuQuant nq = new NeuQuant(pixels, len, sample);
        // initialize quantizer
        colorTab = nq.process(); // create reduced palette
        // convert map from BGR to RGB
        for (int i = 0; i < colorTab.length; i += 3) {
            byte temp = colorTab[i];
            colorTab[i] = colorTab[i + 2];
            colorTab[i + 2] = temp;
            usedEntry[i / 3] = false;
        }
        // map image pixels to new palette
        int k = 0;
        for (int i = 0; i < nPix; i++) {
            int index = nq.map(pixels[k++] & 0xff, pixels[k++] & 0xff, pixels[k++] & 0xff, transparent != -1);
            usedEntry[index] = true;
            indexedPixels[i] = (byte) index;
        }
        pixels = null;
        colorDepth = 8;
        palSize = 7;
        // get closest match to transparent color if specified
        if (transparent != -1) {
            transIndex = findClosest(transparent);
        }
    }

    /**
     * Returns index of palette color closest to c
     */
    private int findClosest(int c) {
        if (colorTab == null)
            return -1;
        int r = (c >> 16) & 0xff;
        int g = (c >> 8) & 0xff;
        int b = (c) & 0xff;
        int minpos = 0;
        int len = colorTab.length;

        //RGB值严格相等才显示透明
        for (int i = 0; i < len; ) {
            int dr = r - (colorTab[i++] & 0xff);
            int dg = g - (colorTab[i++] & 0xff);
            int db = b - (colorTab[i] & 0xff);
            int d = dr * dr + dg * dg + db * db;
            int index = i / 3;
            if (usedEntry[index]
                    && d == 0) {
                minpos = index;
            }
            i++;
        }
        return minpos;
    }

    /**
     * Extracts image pixels into byte array "pixels"
     */
    private void getImagePixels() {
        int w = image.getWidth();
        int h = image.getHeight();
        if ((w != width) || (h != height)) {
            // create new image with right size/format
            Bitmap temp = Bitmap.createBitmap(width, height, Config.ARGB_4444);
            Canvas g = new Canvas(temp);
            g.drawBitmap(image, 0, 0, paint);
            if (!image.isRecycled()) {
                image.recycle();
            }
            image = temp;
        }
        int[] data = getImageData(image);
        pixels = new byte[data.length * 3];

        for (int i = 0; i < data.length; i++) {
            int td = data[i];
            if (td >> 24 != 0) {//去掉透明像素,这里实际上透明像素被赋值为0了
                float r = ((td >> 16) & 0xff);
                float g = ((td >> 8) & 0xff);
                float b = ((td) & 0xff);

                //由于设置了黑色为透明，所以图片里面原本的黑色都需要改为其他颜色
                int tind = i * 3;
                if (r == 0 && g == 0 && b == 0) {
                    pixels[tind++] = (byte) (1);
                    pixels[tind++] = (byte) (1);
                    pixels[tind] = (byte) (1);
                } else {
                    pixels[tind++] = (byte) (b);
                    pixels[tind++] = (byte) (g);
                    pixels[tind] = (byte) (r);
                }
            }
        }
    }


    private int[] getImageData(Bitmap img) {
        int w = img.getWidth();
        int h = img.getHeight();

        int[] data = new int[w * h];
        img.getPixels(data, 0, w, 0, 0, w, h);
        return data;
    }

    /**
     * Writes Graphic Control Extension
     */
    private void writeGraphicCtrlExt() throws IOException {
        out.write(0x21); // extension introducer
        out.write(0xf9); // GCE label
        out.write(4); // data block size
        int transp, disp;
        if (transparent == -1) {
            transp = 0;
            disp = 0; // dispose = no action
        } else {
            transp = 1;
            disp = 2; // force clear if using transparent color
        }
        if (dispose >= 0) {
            disp = dispose & 7; // user override
        }
        disp <<= 2;

        // packed fields
        out.write(0 | // 1:3 reserved
                disp | // 4:6 disposal
                0 | // 7 user input - 0 = none
                transp); // 8 transparency flag

        writeShort(delay); // delay x 1/100 sec
        out.write(transIndex); // transparent color index
        out.write(0); // block terminator
    }

    /**
     * Writes Image Descriptor
     */
    private void writeImageDesc() throws IOException {
        out.write(0x2c); // image separator
        writeShort(x); // image position x,y = 0,0
        writeShort(y);
        writeShort(width); // image size
        writeShort(height);
        // packed fields
        if (firstFrame) {
            // no LCT - GCT is used for first (or only) frame
            out.write(0);
        } else {
            // specify normal LCT
            out.write(0x80 | // 1 local color table 1=yes
                    0 | // 2 interlace - 0=no
                    0 | // 3 sorted - 0=no
                    0 | // 4-5 reserved
                    palSize); // 6-8 size of color table
        }
    }

    /**
     * Writes Logical Screen Descriptor
     */
    private void writeLSD() throws IOException {
        // logical screen size
        writeShort(width);
        writeShort(height);
        // packed fields
        out.write((0x80 | // 1 : global color table flag = 1 (gct used)
                0x70 | // 2-4 : color resolution = 7
                0x00 | // 5 : gct sort flag = 0
                palSize)); // 6-8 : gct size

        out.write(0); // background color index
        out.write(0); // pixel aspect ratio - assume 1:1
    }

    /**
     * Writes Netscape application extension to define repeat count.
     */
    private void writeNetscapeExt() throws IOException {
        out.write(0x21); // extension introducer
        out.write(0xff); // app extension label
        out.write(11); // block size
        writeString("NETSCAPE" + "2.0"); // app id + auth code
        out.write(3); // sub-block size
        out.write(1); // loop sub-block id
        writeShort(repeat); // loop count (extra iterations, 0=repeat forever)
        out.write(0); // block terminator
    }

    /**
     * Writes color table
     */
    private void writePalette() throws IOException {
        out.write(colorTab, 0, colorTab.length);
        int n = (3 * 256) - colorTab.length;
        for (int i = 0; i < n; i++) {
            out.write(0);
        }
    }

    /**
     * Encodes and writes pixel data
     */
    private void writePixels() throws IOException {
        LZWEncoder encoder = new LZWEncoder(width, height, indexedPixels, colorDepth);
        encoder.encode(out);
    }

    /**
     * Write 16-bit value to output stream, LSB first
     */
    private void writeShort(int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    /**
     * Writes string to output stream
     */
    private void writeString(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            out.write((byte) s.charAt(i));
        }
    }
}