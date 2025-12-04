package LDS.Person.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.Memory;

/**
 * 图片转 Data URI 转换器
 * 1. 截取窗口
 * 2. 将图片转换为纯 data:image/... 的 Base64 文本形式
 * 3. 支持模糊查询窗口标题
 * 快捷调用 String DATAUri = processWindow(searchText);
 */
public class ImgToUri {

    private static final String IMG_FOLDER = "img";

    /**
     * 初始化图片文件夹
     */
    static {
        File imgDir = new File(IMG_FOLDER);
        if (!imgDir.exists()) {
            imgDir.mkdirs();
            System.out.println("创建图片文件夹: " + IMG_FOLDER);
        }
    }

    /**
     * 模糊查询窗口（包含匹配）
     * 遍历所有打开的窗口，找到标题包含指定文本的窗口
     * 
     * @param searchText 搜索文本
     * @return 匹配的窗口标题，如果没找到返回 null
     */
    public static String fuzzyFindWindow(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return null;
        }
        
        try {
            final String[] foundWindow = {null};
            
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                try {
                    char[] windowTitle = new char[512];
                    int len = User32.INSTANCE.GetWindowText(hwnd, windowTitle, 512);
                    
                    if (len > 0) {
                        String title = new String(windowTitle, 0, len);
                        
                        // 检查窗口标题是否包含搜索文本（不区分大小写）
                        if (title.toLowerCase().contains(searchText.toLowerCase())) {
                            // 只匹配可见窗口
                            if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                                foundWindow[0] = title;
                                return false; // 停止枚举
                            }
                        }
                    }
                } catch (Exception e) {
                    // 继续枚举
                }
                return true; // 继续枚举下一个窗口
            }, null);
            
            if (foundWindow[0] != null) {
                System.out.println("找到匹配的窗口: " + foundWindow[0]);
            } else {
                System.out.println("未找到包含 '" + searchText + "' 的窗口");
            }
            
            return foundWindow[0];
            
        } catch (Exception e) {
            System.err.println("窗口枚举失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 截取指定窗口
     *
     * @param windowTitle 窗口标题
     * @return 截图文件路径
     */
    public static String captureWindow(String windowTitle) {
        try {
            // 查找指定窗口
            HWND hwnd = User32.INSTANCE.FindWindow(null, windowTitle);
            if (hwnd == null) {
                System.err.println("未找到窗口: " + windowTitle);
                return null;
            }

            // 获取窗口矩形
            RECT rect = new RECT();
            User32.INSTANCE.GetWindowRect(hwnd, rect);
            
            int x = rect.left;
            int y = rect.top;
            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;

            System.out.println("找到窗口: " + windowTitle);
            System.out.println("位置: (" + x + ", " + y + "), 大小: " + width + "x" + height);

            // 使用 Windows API 截取窗口内容
            BufferedImage image = captureWindowByAPI(hwnd, width, height);

            if (image == null) {
                System.err.println("使用 Windows API 截取失败！");
                return null;
            }

            // 生成文件名（去除特殊字符）
            String safeWindowTitle = windowTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
            String screenshotPath = IMG_FOLDER + File.separator + safeWindowTitle + "_screenshot.png";
            
            ImageIO.write(image, "png", new File(screenshotPath));
            System.out.println("窗口截图已保存: " + screenshotPath);

            return screenshotPath;

        } catch (Exception e) {
            System.err.println("截取窗口失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 使用 Windows API 截取窗口内容（即使被覆盖也能截取）
     */
    private static BufferedImage captureWindowByAPI(HWND hwnd, int width, int height) {
        try {
            // 获取窗口设备上下文
            HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
            if (hdcWindow == null) {
                System.err.println("获取窗口设备上下文失败");
                return null;
            }

            // 创建兼容的设备上下文
            HDC hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
            if (hdcMemDC == null) {
                System.err.println("创建兼容设备上下文失败");
                User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
                return null;
            }

            // 创建兼容的位图
            HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, width, height);
            if (hBitmap == null) {
                System.err.println("创建兼容位图失败");
                GDI32.INSTANCE.DeleteDC(hdcMemDC);
                User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
                return null;
            }

            // 选择位图到设备上下文
            WinNT.HANDLE previousBitmap = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

            try {
                // 使用 PrintWindow 将窗口内容绘制到位图（即使被覆盖也能截取）
                final int PW_RENDERFULLCONTENT = 0x00000002;
                boolean captured = User32.INSTANCE.PrintWindow(hwnd, hdcMemDC, PW_RENDERFULLCONTENT);
                
                if (!captured) {
                    System.out.println("PrintWindow 返回 false，尝试 BitBlt...");
                    // 备选方案：使用 BitBlt（可能无法截取被覆盖的窗口）
                    final int SRCCOPY = 0x00CC0020;
                    GDI32.INSTANCE.BitBlt(hdcMemDC, 0, 0, width, height, hdcWindow, 0, 0, SRCCOPY);
                }

                // 将位图转换为 BufferedImage
                return getBufferedImageFromBitmap(hBitmap, hdcMemDC, width, height);

            } finally {
                // 清理资源
                if (previousBitmap != null) {
                    GDI32.INSTANCE.SelectObject(hdcMemDC, previousBitmap);
                }
                GDI32.INSTANCE.DeleteObject(hBitmap);
                GDI32.INSTANCE.DeleteDC(hdcMemDC);
                User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
            }

        } catch (Exception e) {
            System.err.println("Windows API 截取异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将 HBITMAP 转换为 BufferedImage
     */
    private static BufferedImage getBufferedImageFromBitmap(HBITMAP hBitmap, HDC hdc, int width, int height) {
        try {
            WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biSize = bmi.bmiHeader.size();
            bmi.bmiHeader.biWidth = width;
            bmi.bmiHeader.biHeight = -height; // 负数表示从顶向下
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

            long bytes = (long) width * height * 4L;
            Memory buffer = new Memory(bytes);
            
            int result = GDI32.INSTANCE.GetDIBits(hdc, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);
            if (result == 0) {
                System.err.println("GetDIBits 失败");
                return null;
            }

            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int off = (y * width + x) * 4;
                    // BGRA -> ARGB
                    int b = buffer.getByte(off) & 0xFF;
                    int g = buffer.getByte(off + 1) & 0xFF;
                    int r = buffer.getByte(off + 2) & 0xFF;
                    int a = buffer.getByte(off + 3) & 0xFF;
                    
                    // 如果 alpha 为 0，设置为 255（不透明）
                    if (a == 0) {
                        a = 0xFF;
                    }
                    
                    pixels[y * width + x] = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                }
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            return image;

        } catch (Exception e) {
            System.err.println("转换位图失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将图片转换为 Data URI (Base64)
     */
    public static String convertImageToDataUri(String imagePath) {
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                System.err.println("图片不存在: " + imagePath);
                return "";
            }

            // 获取文件扩展名
            String fileName = imageFile.getName().toLowerCase();
            String mimeType = getMimeType(fileName);

            // 读取图片文件为字节数组
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

            // 编码为 Base64
            String base64String = Base64.getEncoder().encodeToString(imageBytes);

            // 构建 Data URI
            String dataUri = "data:" + mimeType + ";base64," + base64String;

            return dataUri;

        } catch (IOException e) {
            System.err.println("读取图片失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 获取文件的 MIME 类型
     */
    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        } else {
            return "image/jpeg"; // 默认
        }
    }

    /**
     * 将 Data URI 保存到文本文件
     */
    @Deprecated
    public static void saveDataUriToFile(String dataUri, String outputPath) {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(dataUri);
            System.out.println("已保存到: " + outputPath);
            System.out.println("文件大小: " + String.format("%.2f", dataUri.length() / 1024.0) + " KB");
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }

    /**
     * 处理窗口截图和转换，返回 Data URI
     * 支持模糊查询窗口标题
     *
     * @param searchText 窗口标题或搜索文本（支持模糊匹配）
     * @return Data URI 字符串，如果失败返回 null
     */
    public static String processWindow(String searchText) {
        // System.out.println("正在处理窗口: " + searchText + "\n");

        // 1. 先尝试精确匹配，如果不存在则进行模糊查询
        HWND hwnd = User32.INSTANCE.FindWindow(null, searchText);
        String actualWindowTitle = searchText;
        
        if (hwnd == null) {
            System.out.println("精确匹配未找到，进行模糊查询...");
            actualWindowTitle = fuzzyFindWindow(searchText);
            
            if (actualWindowTitle == null) {
                System.err.println("模糊查询也未找到窗口！");
                return null;
            }
            
            // 用模糊匹配到的窗口标题再进行一次查询
            hwnd = User32.INSTANCE.FindWindow(null, actualWindowTitle);
        }

        if (hwnd == null) {
            System.err.println("最终窗口查询失败！");
            return null;
        }

        System.out.println("目标窗口: " + actualWindowTitle + "\n");

        // 2. 截取窗口
        String screenshotPath = captureWindow(actualWindowTitle);
        
        if (screenshotPath == null) {
            System.err.println("截图失败！");
            return null;
        }

        System.out.println();

        // 3. 将截图转换为 Data URI
        String dataUri = convertImageToDataUri(screenshotPath);

        if (dataUri.isEmpty()) {
            System.err.println("转换失败!");
            return null;
        }

        // 4. 显示结果
        System.out.println("Data URI 长度: " + dataUri.length() + " 字符");
        System.out.println("Data URI 大小: " + String.format("%.2f", dataUri.length() / 1024.0) + " KB");

        return dataUri;
    }
}
