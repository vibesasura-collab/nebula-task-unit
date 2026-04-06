import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Main {

    private static final int MAX_RUN_MINUTES = 345;
    private static final int WAIT_AFTER_FULL_CYCLE_MINUTES = 55;

    private static final String LOGIN_URL = "https://elem.cards/login/";
    private static final String AUTOTUNE_URL = "https://elem.cards/funnyfights/?autotune=on";
    private static final String ENEMY_URL = "https://elem.cards/funnyfights/enemy/";
    private static final String MANAGE_URL = "https://elem.cards/funnyfights/manage/";
    private static final String FUNNYFIGHTS_HOME_URL = "https://elem.cards/funnyfights/";
    private static final String HOME_URL = "https://elem.cards/";

    public static void main(String[] args) {
        String user = System.getenv("USER_KEY");
        String pass = System.getenv("ACCESS_KEY");

        if (user == null || user.isEmpty() || pass == null || pass.isEmpty()) {
            throw new RuntimeException("USER_KEY or ACCESS_KEY not found in GitHub Secrets.");
        }

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        Instant startTime = Instant.now();

        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

            login(driver, user, pass);

            collectDailyRewardIfAvailable(driver);
            collectFreeGemsIfAvailable(driver);

            while (true) {
                if (shouldStopNow(startTime)) {
                    System.out.println("Stopping due to runtime limit.");
                    break;
                }

                runOneFullCycle(driver);

                if (shouldStopNow(startTime)) {
                    System.out.println("Stopping due to runtime limit.");
                    break;
                }

                System.out.println("Cycle finished. Waiting " + WAIT_AFTER_FULL_CYCLE_MINUTES + " minutes...");
                sleepMinutes(WAIT_AFTER_FULL_CYCLE_MINUTES);

                if (shouldStopNow(startTime)) {
                    System.out.println("Stopping due to runtime limit.");
                    break;
                }

                driver.navigate().refresh();
                sleep(5000);

                collectDailyRewardIfAvailable(driver);
                collectFreeGemsIfAvailable(driver);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Run failed", e);
        } finally {
            driver.quit();
        }
    }

    private static void login(WebDriver driver, String user, String pass) {
        System.out.println("Opening page...");
        driver.get(LOGIN_URL);
        sleep(2000);

        System.out.println("Signing in...");
        driver.findElement(By.name("plogin")).sendKeys(user);
        driver.findElement(By.name("ppass")).sendKeys(pass);
        driver.findElement(By.cssSelector("input[type='submit']")).click();

        sleep(5000);
        System.out.println("Sign-in complete.");
    }

    private static void runOneFullCycle(WebDriver driver) {
        System.out.println("Starting cycle...");

        driver.get(AUTOTUNE_URL);
        sleep(3000);

        boolean anyAttackDone = false;

        for (int i = 1; i <= 5; i++) {
            System.out.println("Checking unit " + i);

            driver.get(ENEMY_URL + i + "/");
            sleep(2500);

            List<WebElement> attackBtns = driver.findElements(
                By.xpath("//a[contains(@href,'/funnyfights/attack/') and .//span[text()='Attack!']]")
            );

            if (attackBtns.isEmpty()) {
                System.out.println("Attack not available for unit " + i + ". Skipping.");
                continue;
            }

            String attackLink = attackBtns.get(0).getAttribute("href");

            if (attackLink == null || attackLink.isEmpty()) {
                System.out.println("Attack link empty for unit " + i + ". Skipping.");
                continue;
            }

            anyAttackDone = true;
            System.out.println("Attacking unit " + i + " with link: " + attackLink);

            driver.get(attackLink);
            sleep(5000);

            System.out.println("Attack finished for unit " + i);

            driver.get(MANAGE_URL);
            sleep(2500);

            clickUpgradeIfAvailable(driver);

            driver.get(AUTOTUNE_URL);
            sleep(2500);
        }

        if (!anyAttackDone) {
            System.out.println("No actions available in this cycle.");
        }
    }

    private static void clickUpgradeIfAvailable(WebDriver driver) {
        try {
            List<WebElement> upgradeBtns = driver.findElements(
                By.xpath("//a[contains(@href,'/funnyfights/manage/upgrade/0/')]")
            );

            if (!upgradeBtns.isEmpty()) {
                String upgradeLink = upgradeBtns.get(0).getAttribute("href");

                if (upgradeLink != null && !upgradeLink.isEmpty()) {
                    System.out.println("Optional step available: " + upgradeLink);
                    driver.get(upgradeLink);
                    sleep(2500);
                    return;
                }
            }

            System.out.println("Optional step not available. Skipping.");
        } catch (Exception e) {
            System.out.println("Optional step failed. Skipping.");
        }
    }

    private static void collectFreeGemsIfAvailable(WebDriver driver) {
        try {
            System.out.println("Checking free gems...");

            driver.get(FUNNYFIGHTS_HOME_URL);
            sleep(2500);

            List<WebElement> freeGemBtns = driver.findElements(
                By.xpath("//a[contains(@href,'/funnyfights/freegems/') and .//span[text()='Take']]")
            );

            if (!freeGemBtns.isEmpty()) {
                String freeGemLink = freeGemBtns.get(0).getAttribute("href");

                if (freeGemLink != null && !freeGemLink.isEmpty()) {
                    System.out.println("Free gems available: " + freeGemLink);
                    driver.get(freeGemLink);
                    sleep(2500);
                    return;
                }
            }

            System.out.println("Free gems not available. Skipping.");
        } catch (Exception e) {
            System.out.println("Free gems step failed. Skipping.");
        }
    }

    private static void collectDailyRewardIfAvailable(WebDriver driver) {
        try {
            System.out.println("Checking daily reward...");

            driver.get(HOME_URL);
            sleep(2500);

            List<WebElement> dailyRewardBtns = driver.findElements(
                By.xpath("//a[contains(@href,'/dailyreward/tnx/') and .//span[text()='Receive']]")
            );

            if (!dailyRewardBtns.isEmpty()) {
                String dailyRewardLink = dailyRewardBtns.get(0).getAttribute("href");

                if (dailyRewardLink != null && !dailyRewardLink.isEmpty()) {
                    System.out.println("Daily reward available: " + dailyRewardLink);
                    driver.get(dailyRewardLink);
                    sleep(2500);
                    return;
                }
            }

            System.out.println("Daily reward not available. Skipping.");
        } catch (Exception e) {
            System.out.println("Daily reward step failed. Skipping.");
        }
    }

    private static boolean shouldStopNow(Instant startTime) {
        long elapsedMinutes = Duration.between(startTime, Instant.now()).toMinutes();
        return elapsedMinutes >= MAX_RUN_MINUTES;
    }

    private static void sleepMinutes(int minutes) {
        sleep(minutes * 60L * 1000L);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
