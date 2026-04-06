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
    private static final String ATTACK_URL = "https://elem.cards/funnyfights/attack/";
    private static final String MANAGE_URL = "https://elem.cards/funnyfights/manage/";

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
                By.xpath("//a[contains(@href,'/funnyfights/attack/')]")
            );

            if (attackBtns.isEmpty()) {
                System.out.println("Action not available for unit " + i + ". Skipping.");
                continue;
            }

            anyAttackDone = true;
            System.out.println("Running action for unit " + i);

            driver.get(ATTACK_URL);
            sleep(5000);

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
                    System.out.println("Optional step available.");
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
