import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.openqa.selenium.interactions.PointerInput.Kind.MOUSE;

/**
 * Description: 实现滑动验证码的验证
 * @date:2021/03/30 09:23
 * @author: lyf
 */
public class SildeCode {

    private WebDriver driver;

    private Actions actions;

    private WebElement element;

    private JavascriptExecutor js;

    private BufferedImage imgBefore;  // 带有缺口的验证码

    private BufferedImage imgAfter;  // 不带有缺口的验证码


    /**
     * 初始化操作
     */
    public void init() {
        driver = new ChromeDriver();
        driver.manage().window().maximize();
        driver.get("http://www.geetest.com/Register");
        js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollTo(1,100)");
        actions = new Actions(driver);
    }


    /**
     * Selenium方法等待元素出现
     * @param driver 驱动
     * @param by 元素定位方式
     * @return 元素控件
     */
    public static WebElement WaitMostSeconds(WebDriver driver, By by) {
        try {
            WebDriverWait AppiumDriverWait = new WebDriverWait(driver, 5);
            return (WebElement) AppiumDriverWait.until(ExpectedConditions
                    .presenceOfElementLocated(by));
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new NoSuchElementException("元素控件未出现");
    }


    /**
     * 保存截图的方法
     * @param screen 元素截图
     * @param name 截图保存名字
     */
    public void savePng(File screen, String name) {
        String screenShortName = name + ".png";
        try {
            System.out.println("save screenshot");
            FileUtils.copyFile(screen, new File(screenShortName));
        } catch (IOException e) {
            System.out.println("save screenshot fail");
            e.printStackTrace();
        } finally {
            System.out.println("save screenshot finish");
        }
    }

    /**
     * 获取无缺口的验证码和带有缺口的验证码
     */
    public void saveCode() {
        // 获取无缺口的截图
        js.executeScript("document.querySelectorAll('canvas')[2].style=''");
        element = WaitMostSeconds(driver, By.cssSelector("div.geetest_window"));
        File screen = element.getScreenshotAs(OutputType.FILE); //执行屏幕截取
        savePng(screen, "无缺口");

        // 获取有缺口的截图
        js.executeScript("document.querySelectorAll('canvas')[2].classList=[]");
        element = WaitMostSeconds(driver, By.cssSelector("div.geetest_window"));
        screen = element.getScreenshotAs(OutputType.FILE); //执行屏幕截取
        savePng(screen, "有缺口");
    }

    /**
     * 比较两张截图上的当前像素点的RGB值是否相同
     * 只要满足一定误差阈值，便可认为这两个像素点是相同的
     * @param x 像素点的x坐标
     * @param y 像素点的y坐标
     * @return true/false
     */
    public boolean equalPixel(int x, int y) {
        int rgbaBefore = imgBefore.getRGB(x, y);
        int rgbaAfter = imgAfter.getRGB(x, y);
        // 转化成RGB集合
        Color colBefore = new Color(rgbaBefore, true);
        Color colAfter = new Color(rgbaAfter, true);
        int threshold = 80;   // RGB差值阈值
        if (Math.abs(colBefore.getRed() - colAfter.getRed()) < threshold &&
            Math.abs(colBefore.getGreen() - colAfter.getGreen()) < threshold &&
            Math.abs(colBefore.getBlue() - colAfter.getBlue()) < threshold)  {
            return true;
        }
        return false;
    }


    /**
     * 比较两张截图，找出有缺口的验证码截图中缺口所在位置
     * 由于滑块是x轴方向位移，因此只需要x轴的坐标即可
     * @return 缺口起始点x坐标
     * @throws Exception
     */
    public int getGap() throws Exception {
        imgBefore = ImageIO.read(new File("缺口.png"));
        imgAfter = ImageIO.read(new File("无缺口.png"));
        int width = imgBefore.getWidth();
        int height = imgBefore.getHeight();
        int pos = 60;  // 小方块的固定起始位置
        // 横向扫描
        for (int i = pos; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (!equalPixel(i,j)) {
                    pos = i;
                    return pos;
                }
            }
        }
        throw new Exception("未找到滑块缺口");
    }


    /**
     * 计算滑块到达目标点的运行轨迹
     * 先加速，后减速
     * @param distance 目标距离
     * @return 运动轨迹
     */
    public List<Integer> trace(int distance) {
        java.util.List<Integer> moveTrace = new ArrayList<>();
        int current = 0;  // 当前位移
        int threshold = distance * 3/5; // 减速阈值
        double t = 0.2;   // 计算间隔
        double v = 0.0;     // 初速度
        double a;     // 加速度
        while (current < distance) {
            if (current < threshold) {
                a = 2;
            } else {
                a = -4;
            }
            // 位移计算公式
            double tmp = v;
            // 移动速度，会出现负值的情况，然后往反方向拉取
            v = tmp + a*t;
            int move = (int) (tmp*t + 0.5*a*t*t);
            current += move;
            moveTrace.add(move);
        }
        // 考虑到最后一次会超出移动距离，将其强制修改回来，不允许超出
        int length = moveTrace.size();
        moveTrace.set(length-1, moveTrace.get(length-1) + (current > distance ? -(current-distance): 0));
        return moveTrace;
    }

    /**
     * 消除selenium中移动操作的卡顿感
     * 这种卡顿感是因为selenium中自带的moveByOffset是默认有200ms的延时的
     * 可参考:https://blog.csdn.net/fx9590/article/details/113096513
     * @param x x轴方向位移距离
     * @param y y轴方向位移距离
     */
    public void moveWithoutWait(int x, int y) {
        PointerInput defaultMouse = new PointerInput(MOUSE, "default mouse");
        actions.tick(defaultMouse.createPointerMove(Duration.ofMillis(0), PointerInput.Origin.pointer(), x, y)).perform();
    }


    /**
     * 移动滑块，实现验证
     * @param moveTrace 滑块的运动轨迹
     * @throws Exception
     */
    public void move(List<Integer> moveTrace) throws Exception {
        // 获取滑块对象
        element = WaitMostSeconds(driver, By.cssSelector("div.geetest_slider_button"));
        // 按下滑块
        actions.clickAndHold(element).perform();
        Iterator it = moveTrace.iterator();
        while (it.hasNext()) {
            // 位移一次
            int dis = (int) it.next();
            moveWithoutWait(dis, 0);
        }
        // 模拟人的操作，超过区域
        moveWithoutWait(5, 0);
        moveWithoutWait(-3, 0);
        moveWithoutWait(-2, 0);
        // 释放滑块
        actions.release().perform();
        Thread.sleep(500);
    }


    /**
     * 调出验证码时的一些准备工作
     * @throws Exception
     */
    public void prepare() throws Exception {
        // 调出验证码
        element = WaitMostSeconds(driver, By.cssSelector("div.phone > input"));
        element.clear();
        element.sendKeys("12345678910");
        element = WaitMostSeconds(driver, By.cssSelector("div.sendCode"));
        element.click();

        // 等待验证码出现
        element = WaitMostSeconds(driver, By.cssSelector("a.geetest_close"));
        Thread.sleep(500);
    }


    public static void main(String[] args) throws Exception{
        SildeCode sc = new SildeCode();
        sc.init();
        sc.prepare();
        int left = sc.getGap();
        // 验证码的边界差值
        left -= 7;
        List<Integer> moveTrace = sc.trace(left);
        sc.move(moveTrace);
    }

}
