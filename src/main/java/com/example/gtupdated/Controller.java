/*
* 10/31/2025追記
*==== To-Be ====
*   - UIの調整
*     与えられた枠ぴったりに配置する方法とアコーディオンを開閉したとき自動でアプリの縦幅を調節する機能とデフォルト配置を左ペイン最下部に
*   　それからウィジェットと枠との間の長さを統一、必要なのはchangelogと左ペインのウィジェットはすべて統一
*
* */
package com.example.gtupdated;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Controller {

    private Stage primaryStage;
    private static final String APP_VERSION = "1.1.2";
    private static final String VERSION_URL = "https://gomadare-modpack-updater.s3.ap-northeast-1.amazonaws.com/version.json";

    @FXML private Button updateButton;
    @FXML private ProgressBar mainProgressBar;
    @FXML private AnchorPane leftPaneContainer;
    @FXML private AnchorPane rightPaneContainer;
    @FXML private TextField directoryTextField;
    @FXML private Button selectDirectoryButton;
    @FXML private Label pathLabel;
    @FXML private TextArea changelogArea;
    @FXML private TextField statusLabel;
    @FXML private TextField debugPathInput;
    @FXML private Accordion debugAccordion;
    @FXML private TitledPane debugPane;
    @FXML private Button selectDebugZipButton;
    @FXML private Button localTestButton;
    @FXML private Button popupTestButton;
    @FXML private Button progressBarTestButton;
    @FXML private Button applyDirectoryButton;
    @FXML private Button selectDebugFileButton;
    @FXML private CheckBox popupTestCheckBox;
    @FXML private CheckBox progressBarTestCheckBox;
    @FXML private Label versionLabel;

    private String currentModpackVersion = "0.0.0"; // アプリ起動時のデフォルト
    private String latestVersionFound = ""; // S3から取得した最新バージョンを一時保存
    public void setStage(Stage stage) {this.primaryStage = stage;}
    private final Properties properties = new Properties();
    private final File configFile = new File("config.properties");


    private Path getConfigFilePath() {
        // ユーザーのホームディレクトリを取得 (例: C:\Users\goma)
        String userHome = System.getProperty("user.home");
        // アプリケーション用の設定フォルダのパスを構築 (例: C:\Users\goma\AppData\Roaming\ModpackUpdater)
        // OSごとに適切な場所を自動で選択してくれます
        Path configDir = Paths.get(userHome, "AppData", "Roaming", "ModpackUpdater");
        // 設定ファイルのフルパスを返す
        return configDir.resolve("config.properties");
    }
    private void saveProperties(String gameDirectory) {
        Path configFile = getConfigFilePath();
        try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8)) {

            // ★ 保存するプロパティをセット
            if (gameDirectory != null && !gameDirectory.isEmpty()) {
                properties.setProperty("game_directory", gameDirectory);
            }
            // ★ 現在のModpackバージョンを保存
            properties.setProperty("current_modpack_version", this.currentModpackVersion);

            // アコーディオン内のデバッグパスも保存（既存のロジック）
            if (debugPathInput != null) {
                properties.setProperty("debug_local_zip_path", debugPathInput.getText());
            }

            properties.store(out, "Modpack Updater Settings");
            log("設定を " + configFile.toString() + " に保存しました。");
        } catch (IOException e) {
            log("設定の保存中にエラーが発生しました: " + e.getMessage() + "\n");
        }
    }
    /**
     * config.propertiesファイルから設定を読み込み、UIに反映します。
     */
    private void loadProperties() {
        Path configFile = getConfigFilePath();

        if (!Files.exists(configFile)) {
            log("設定ファイルが見つかりません。初回起動です。");
            Platform.runLater(() -> {
                versionLabel.setText("現在のVer: (不明)");
            });
            return;
        }

        try (InputStreamReader in = new InputStreamReader(new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
            properties.load(in);

            // ゲームディレクトリのパスを取得
            String gameDirectory = properties.getProperty("game_directory");
            // デバッグ用Zipのパスを取得（デフォルト値は "upgrade.zip"）
            String debugZipPath = properties.getProperty("debug_local_zip_path", "upgrade.zip");
            this.currentModpackVersion = properties.getProperty("current_modpack_version", "0.0.0"); // デフォルトは"0.0.0"

            // UIの更新は、必ずJavaFXアプリケーションスレッドで行います
            Platform.runLater(() -> {
                // ゲームディレクトリのUIを更新
                if (gameDirectory != null && !gameDirectory.isEmpty()) {
                    // 1. 入力欄（TextField）にパスを設定
                    directoryTextField.setText(gameDirectory);
                    // 2. 表示用ラベルにもパスを設定
                    pathLabel.setText("ゲームディレクトリ: " + gameDirectory);

                    log("設定を " + configFile.toString() + " から読み込みました。");
                }
                versionLabel.setText("現在のVer: " + this.currentModpackVersion);
                // デバッグ用UI（アコーディオン内のTextField）を更新
                // FXMLに fx:id="debugPathInput" が定義されていれば、nullではなく正しく設定されます
                if (debugPathInput != null) {
                    debugPathInput.setText(debugZipPath);
                }
            });

        } catch (IOException e) {
            log("設定の読み込み中にエラーが発生しました: " + e.getMessage());
        }
    }
    private void checkForUpdates() {
        log("サーバーにバージョン情報を問い合わせています...");
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(VERSION_URL)).build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        String jsonBody = response.body();
                        Gson gson = new Gson();
                        JsonObject versionInfo = gson.fromJson(jsonBody, JsonObject.class);
                        String latestVersion = versionInfo.get("latest_version").getAsString();
                        this.latestVersionFound = latestVersion;
                        if (latestVersion.compareTo(this.currentModpackVersion) > 0) {
                            String downloadUrl = versionInfo.get("download_url").getAsString();
                            String changelog = versionInfo.get("changelog").getAsString();
                            Platform.runLater(() -> {
                                if (changelogArea != null) {
                                    changelogArea.setText(changelog);
                                }
                                showUpdatePopup(latestVersion, changelog, downloadUrl);
                            });
                        } else {
                            Platform.runLater(() -> {
                                log("お使いのバージョンは最新です。");
                            });
                        }
                    })
                    .exceptionally(e -> {
                        Platform.runLater(() -> log("エラー: アップデート情報の取得に失敗 - " + e.getMessage()));
                        return null;
                    });
        }
    }

    // FXMLがロードされた直後に自動で呼ばれる初期化メソッド
    @FXML
    void initialize() {
        // アプリ起動時の初期設定をここに書く
        log("アプリケーションを起動しました。");
        mainProgressBar.setProgress(0.0);

        // directoryTextField の初期テキストを "aaa" から変更する場合
        //directoryTextField.setText("/home/goma/...");

        loadProperties();

    }
    @FXML
    protected void onUpdateButtonClick() {
        // ロジックはここに書く
        log("アップデートボタンがクリックされました！");
        log("アップデートの確認を開始します...");

        checkForUpdates();
    }
    @FXML
    protected void onSelectDirectoryClick() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Minecraftのゲームディレクトリを選択してください");

        // 以前のパスがあれば、そこを初期表示する
        File currentDir = new File(directoryTextField.getText());
        if (currentDir.isDirectory()) {
            chooser.setInitialDirectory(currentDir);
        }

        File selectedDirectory = chooser.showDialog(this.primaryStage);
        if (selectedDirectory != null) {
            // ★ 選択したパスをTextFieldに書き込むだけ
            directoryTextField.setText(selectedDirectory.getAbsolutePath());
            log("パスを選択しました。「適用」ボタンを押して設定を確定してください。");
        } else {
            log("フォルダ選択がキャンセルされました。");
        }
    }
    // ★ FXMLの onAction="#onSelectDebugZipClick" に対応
    @FXML
    protected void onSelectDebugZipClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("テスト用のupgrade.zipを選択");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP Archives", "*.zip"));
        File selectedFile = chooser.showOpenDialog(this.primaryStage);

        if (selectedFile != null) {
            // 仕様通り、TextFieldにパスを書き込むだけ（保存はしない）
            debugPathInput.setText(selectedFile.getAbsolutePath());
            log("テスト用Zipファイルを選択しました。");
        }
    }
    @FXML
    protected void onTestProgressBarClick() {
        log("プログレスバーのテストが開始されました。");
        log("プログレスバーのテスト中...");
        Task<Void> progressTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                // 0から100までの進捗をシミュレート
                for (int i = 0; i <= 100; i++) {
                    // updateProgress(現在の値, 最大値)
                    // これで進捗がUIスレッドに通知される
                    updateProgress(i, 100);

                    // 20ミリ秒待機（処理が速すぎると見えないため）
                    Thread.sleep(20);
                }
                return null;
            }
        };


        // Taskが完了したら（成功したら）実行する処理
        progressTask.setOnSucceeded(event -> {
            log("プログレスバーのテスト完了。");
        });

        // Taskが失敗したら実行する処理
        progressTask.setOnFailed(event -> {
            log("エラー発生。");
        });

        // Taskの進捗(progressProperty)を、ProgressBarの進捗(progressProperty)に結びつける
        mainProgressBar.progressProperty().bind(progressTask.progressProperty());

        // 新しいスレッドを作成し、そのスレッドでTaskを実行する
        // (これを行わないとUIがフリーズします)
        new Thread(progressTask).start();
    }
    @FXML
    protected void onApplyDirectoryClick() {
        String newPath = directoryTextField.getText();
        if (newPath == null || newPath.isEmpty()) {
            log("エラー: パスが入力されていません。");
            return;
        }

        File file = new File(newPath);
        if (!file.isDirectory()) {
            log("エラー: 指定されたパスは有効なディレクトリではありません。");
            return;
        }

        // 1. pathLabel（表示用ラベル）を更新
        pathLabel.setText("ゲームディレクトリ: " + newPath);

        // 2. 設定ファイルに保存
        saveProperties(newPath); // (古いHelloApplicationから移植したメソッド)

        log("ゲームディレクトリが設定されました: " + newPath);
    }
    private void showUpdatePopup(String newVersion, String changelog, String downloadUrl) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("GT Updated vx.x.x");
        alert.setHeaderText(null); // ヘッダーテキストは不要なのでnullに

        // --- カスタムボタンの作成 ---
        ButtonType updateButtonType = new ButtonType("更新", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipButtonType = new ButtonType("スキップ", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(updateButtonType, skipButtonType);

        // --- カスタムレイアウトの作成 ---
        Label titleLabel = new Label("新しいアップデートがありました");
        Label versionFlowLabel = new Label("ver" + APP_VERSION + " → ver" + newVersion);
        TextArea changelogArea = new TextArea(changelog);
        changelogArea.setEditable(false);
        changelogArea.setWrapText(true);

        VBox content = new VBox(10, titleLabel, versionFlowLabel, changelogArea);
        content.setPadding(new Insets(10));

        // --- アラートにカスタムレイアウトを設定 ---
        alert.getDialogPane().setContent(content);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == updateButtonType) {
            startDownload(downloadUrl);
        } else {
            log("アップデートがキャンセルされました。");
        }
    }
    private void startDownload(String url) {
        mainProgressBar.setVisible(true);
        log("アップデートファイルのダウンロードを開始します...\nURL: " + url + "\n");

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                // try-with-resourcesでHttpClientを管理
                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        throw new IOException("サーバーからの応答が不正です: " + response.statusCode());
                    }

                    long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    File downloadedFile = new File("upgrade.zip");
                    long bytesRead = 0;
                    byte[] buffer = new byte[8192];
                    int read;

                    try (InputStream in = response.body(); FileOutputStream out = new FileOutputStream(downloadedFile)) {
                        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                            out.write(buffer, 0, read);
                            bytesRead += read;
                            updateProgress(bytesRead, totalBytes);
                        }
                    }
                    return downloadedFile;
                }
            }
        };

        mainProgressBar.progressProperty().bind(downloadTask.progressProperty());

        downloadTask.setOnSucceeded(event -> {
            File downloadedFile = downloadTask.getValue();
            log("ダウンロードが完了しました: " + downloadedFile.getName() + "\n");
            mainProgressBar.progressProperty().unbind();
            mainProgressBar.setProgress(1);

            log("アップデートファイルを解凍しています...\n");
            try {
                unzip(downloadedFile.getPath(), "temp_upgrade");
                log("解凍が完了しました。\n");
                processManifest("temp_upgrade/manifest.json");
            } catch (IOException e) {
                log("エラー: ファイルの解凍に失敗しました - " + e.getMessage() + "\n");
            }
        });

        downloadTask.setOnFailed(event -> {
            log("エラー: ダウンロードに失敗しました - " + downloadTask.getException().getMessage() + "\n");
            mainProgressBar.setVisible(false);
            mainProgressBar.progressProperty().unbind();
        });

        new Thread(downloadTask).start();
    }

    private void processManifest(String manifestPath) throws IOException {
        log("アップグレード指示ファイル (manifest.json) を読み込んでいます...\n");

        String gameDirectoryPath = properties.getProperty("game_directory");
        if (gameDirectoryPath == null || gameDirectoryPath.isEmpty()) {
            log("エラー: ゲームディレクトリが設定されていません。ファイル操作を中断します。\n");
            return;
        }

        Path gameDir = Paths.get(gameDirectoryPath);
        Path tempDir = Paths.get("temp_upgrade");

        String content = Files.readString(Paths.get(manifestPath));
        Gson gson = new Gson();
        Manifest manifest = gson.fromJson(content, Manifest.class);

        if (manifest != null && manifest.operations != null) {
            log("ファイル操作を開始します...\n");
            for (UpdateOperation op : manifest.operations) {
                try {
                    if ("delete".equals(op.action)) {
                        Path targetPath = gameDir.resolve(op.target);
                        Files.deleteIfExists(targetPath);
                        log("  - 削除完了: " + op.target + "\n");
                    } else if ("copy".equals(op.action)) {
                        Path sourcePath = tempDir.resolve(op.source);
                        Path destinationPath = gameDir.resolve(op.destination);

                        // コピー先の親ディレクトリが存在しない場合は作成
                        Files.createDirectories(destinationPath.getParent());

                        // ファイルかフォルダかを判断してコピー
                        if (Files.isDirectory(sourcePath)) {
                            copyDirectory(sourcePath, destinationPath);
                        } else {
                            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                        log("  - コピー完了: " + op.source + " -> " + op.destination + "\n");
                    }
                } catch (IOException e) {
                    log("  - 操作失敗: " + op.action + " - " + e.getMessage() + "\n");
                }
            }
            log("ファイル操作が完了しました。\n");

            // ★★★ 後処理（一時ファイルの削除） ★★★
            cleanup();
            // 1. 現在のModpackバージョンを、S3から取得した最新バージョンに更新
            this.currentModpackVersion = this.latestVersionFound;
            // 2. 更新したバージョンを config.properties に保存
            //    (gameDirectoryは既存の値をそのまま保存)
            saveProperties(properties.getProperty("game_directory"));

            // 3. UIのバージョンラベルも更新
            Platform.runLater(() -> {
                versionLabel.setText("現在のVer: " + this.currentModpackVersion);
            });
            log("アップデートが正常に完了しました！\n");

            // 完了ポップアップを表示
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("成功");
                alert.setHeaderText("アップデートが完了しました。");
                alert.setContentText("アプリケーションを終了してください。");
                alert.showAndWait();
            });

        } else {
            log("警告: manifest.jsonに有効な操作が見つかりませんでした。\n");
        }
    }

    // ★★★ フォルダを再帰的にコピーするためのヘルパーメソッド ★★★
    private void copyDirectory(Path source, Path destination) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path destinationPath = destination.resolve(source.relativize(sourcePath));
                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Platform.runLaterでUIスレッドにエラーメッセージを渡す
                Platform.runLater(() -> log("  - コピーエラー: " + e.getMessage() + "\n"));
            }
        });
    }

    // ★★★ 一時ファイルを削除するためのヘルパーメソッド ★★★
    private void cleanup() {
        log("一時ファイルをクリーンアップしています...\n");
        try {
            Files.deleteIfExists(Paths.get("upgrade.zip"));
            Path tempUpgradeDir = Paths.get("temp_upgrade");
            if (Files.exists(tempUpgradeDir)) {
                // try-with-resourcesでStreamを管理
                try (Stream<Path> walk = Files.walk(tempUpgradeDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
            log("クリーンアップが完了しました。\n");
        } catch (IOException e) {
            log("エラー: 一時ファイルのクリーンアップに失敗 - " + e.getMessage() + "\n");
        }
    }
    private void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            if (!destDir.mkdir()) {
                throw new IOException("出力先ディレクトリの作成に失敗しました: " + destDirectory);
            }
        }
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                if (!newFile.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Zip Slip 攻撃の可能性があります: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("ディレクトリの作成に失敗しました: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("ディレクトリの作成に失敗しました: " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }
    private void handleDirectorySelection() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Minecraftのゲームディレクトリを選択してください");
        File selectedDirectory = chooser.showDialog(this.primaryStage);
        if (selectedDirectory != null) {
            updatePath(selectedDirectory.getAbsolutePath());
        } else {
            log("フォルダ選択がキャンセルされました。\n");
        }
    }

    private void handlePathInput() {
        String pathFromInput = directoryTextField.getText();
        if (pathFromInput != null && !pathFromInput.isEmpty()) {
            updatePath(pathFromInput);
        } else {
            log("パスが入力されていません。\n");
        }
    }
    private void updatePath(String newPath) {
        directoryTextField.setText(newPath);
        pathLabel.setText("ゲームディレクトリ: " + newPath);
        log("ゲームディレクトリが設定されました: " + newPath + "\n");
        saveProperties(newPath);
    }
    /**
     * ログをコンソールとGUIのステータスバーの両方に出力します。
     * @param message 表示するログメッセージ
     */
    private void log(String message) {
        // 1. すべてのログをコンソール（System.out）に出力
        System.out.println("[App Log] " + message);

        // 2. GUIのステータスバー（logTextField）を更新
        // バックグラウンドスレッドから呼ばれても安全なようにPlatform.runLaterで囲む
        Platform.runLater(() -> {
            // ★ 修正点: log(message) ではなく、UI要素を直接更新する
            statusLabel.setText(message);
        });
    }

    @FXML
    protected void onTestPopupClick() {
        System.out.println("ポップアップのテストが実行されました。");
        log("テスト用ポップアップを表示します。");

        // 1. Alertオブジェクトを作成
        // Alert.AlertType.INFORMATION は「情報」ダイアログ（iアイコン）
        // 他に .WARNING (警告), .ERROR (エラー), .CONFIRMATION (はい/いいえ) があります
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        // 2. ダイアログの各部分のテキストを設定
        alert.setTitle("テストポップアップ");
        alert.setHeaderText("これはダミーの通知です。");
        alert.setContentText("FXMLとコントローラーが正しく連携しました！");

        // 3. ダイアログを表示し、ユーザーが閉じるまで待機
        alert.showAndWait();
    }
    @FXML
    void onLocalTestClick() {
        String localZipPath = debugPathInput.getText();

        if (localZipPath == null || localZipPath.isEmpty()) {
            log("エラー: テスト用Zipパスが指定されていません。");
            return;
        }

        log("ローカルテストを開始します...");
        log("ローカルの '" + localZipPath + "' を解凍しています...");

        try {
            // 移植したロジックを実行
            unzip(localZipPath, "temp_upgrade");
            log("解凍が完了しました。");
            processManifest("temp_upgrade/manifest.json");
        } catch (IOException e) {
            log("エラー: ローカルテスト中に失敗しました - " + e.getMessage());
        }
    }
}