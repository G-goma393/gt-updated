package com.example.gtupdated;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task; // ★ Taskをインポート
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream; // ★ ダウンロードストリーム用
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Properties;

import java.io.File;//fileクラスのインポート

/**
 * アプリケーションのメインクラスです。
 * JavaFXのApplicationクラスを継承します。
 */
public class HelloApplication extends Application {

    private Stage primaryStage;

    private Label pathLabel;
    private TextField pathInput;
    private TextArea logArea;
    private ProgressBar progressBar; // ★ ProgressBarをメンバー変数として追加


    private final Properties properties = new Properties();
    private final File configFile = new File("config.properties");

    @Override
    public void start(Stage primaryStage) {
        // 1. ウィンドウのタイトルを設定する
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Modpack Updater");

        // 2. ウィンドウの中身（レイアウト）を作成する
        //    Paneは、コンポーネントを自由に配置できる最もシンプルな土台です。
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label infoLabel = new Label("1.下のボタンからディレクトリを選択するかパスを直接入力してください");
        pathLabel = new Label("ゲームディレクトリ：（未設定）");

        pathInput = new TextField();
        pathInput.setPromptText("ここにゲームディレクトリのパスを貼り付け");
        logArea = new TextArea();
        logArea.setPromptText("処理ログ");
        logArea.setEditable(false);//ユーザーが編集できないようにする

        Button selectDirButton = new Button("ゲームディレクトリを選択...");
        Button setPathButton = new Button("設定...");
        HBox pathInputBox = new HBox(5, pathInput, selectDirButton, setPathButton);
        Button updateButton = new Button("アップデート確認");

        // ★ ProgressBarを作成
        progressBar = new ProgressBar(0); // 初期進捗は0
        progressBar.prefWidthProperty().bind(root.widthProperty()); // 幅をウィンドウに合わせる
        progressBar.setVisible(false); // 最初は非表示にしておく

        //　3.イベント発火内部処理
        selectDirButton.setOnAction(event -> handleDirectorySelection());

        setPathButton.setOnAction(event -> handlePathInput());

        updateButton.setOnAction(event -> {
            logArea.appendText("「アップデートを確認」ボタンが押されました。\n");
        });

        root.getChildren().addAll(
                infoLabel,
                pathInputBox,
                pathLabel,
                updateButton,
                logArea
        );

        loadProperties();
        root.getChildren().addAll(infoLabel, pathInputBox, pathLabel, updateButton, logArea, progressBar);

        //シーンのサイズを調整
        Scene scene = new Scene(root, 500, 350);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    private void showUpdatePopup(String newVersion, String changelog, String downloadUrl) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("アップデートが見つかりました");
        alert.setHeaderText("新しいバージョン " + newVersion + " が利用可能です。");
        alert.setContentText("更新内容:\n" + changelog);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // ★ OKが押されたらダウンロード処理を開始
            startDownload(downloadUrl);
        } else {
            logArea.appendText("アップデートがキャンセルされました。\n");
        }
    }

    // ★ アップデートファイルをダウンロードするメソッド
    private void startDownload(String url) {
        progressBar.setVisible(true); // ProgressBarを表示
        logArea.appendText("アップデートファイルのダウンロードを開始します...\nURL: " + url + "\n");

        // Taskは、時間のかかる処理をバックグラウンドで行うためのツール
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    long bytesRead = 0;
                    byte[] buffer = new byte[8192]; // 8KBのバッファ
                    int read;

                    try (InputStream in = response.body();
                         FileOutputStream out = new FileOutputStream("upgrade.zip")) {

                        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                            out.write(buffer, 0, read);
                            bytesRead += read;
                            // ★ UIに進捗状況を通知する
                            updateProgress(bytesRead, totalBytes);
                        }
                    }
                    return null; // 成功
                } else {
                    throw new IOException("サーバーからの応答が不正です: " + response.statusCode());
                }
            }
        };

        // Taskの進捗とProgressBarの進捗を連動させる
        progressBar.progressProperty().bind(downloadTask.progressProperty());

        // Taskが完了した時の処理
        downloadTask.setOnSucceeded(event -> {
            logArea.appendText("ダウンロードが完了しました: upgrade.zip\n");
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1); // 100%にする
            // ★★★ ここに次のステップである「Zip解凍とファイル操作」の処理を呼び出す ★★★
        });

        // Taskが失敗した時の処理
        downloadTask.setOnFailed(event -> {
            logArea.appendText("エラー: ダウンロードに失敗しました - " + downloadTask.getException().getMessage() + "\n");
            progressBar.setVisible(false);
            progressBar.progressProperty().unbind();
        });

        // 新しいスレッドでTaskを開始
        new Thread(downloadTask).start();
    }
    private void handleDirectorySelection(){
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("ゲームディレクトリを選択してください");
        File selectedDirectory = chooser.showDialog(this.primaryStage);

        if (selectedDirectory != null){
            updatePath(selectedDirectory.getAbsolutePath());
        }else{
            logArea.appendText("フォルダ選択がキャンセルされました\n");
        }
    }

    private void handlePathInput(){
        String pathFromInput = pathInput.getText();
        if(pathFromInput != null && !pathFromInput.isEmpty()){
            updatePath(pathFromInput);
        }else{
            logArea.appendText("パスが入力されていません\n");
        }
    }

    private void updatePath(String newPath){
        pathInput.setText(newPath);
        pathLabel.setText("ゲームディレクトリ：" + newPath);
        logArea.appendText("ゲームディレクトリが設定されました：" + newPath + "\n");
        saveProperties(newPath);
    }

    private void saveProperties(String gameDirectory){
        try (FileOutputStream out = new FileOutputStream(configFile)){
            properties.setProperty("game_directory", gameDirectory);
            properties.store(out, "Modpack Updater Settings");
            logArea.appendText("設定をconfig.propertiesに保存しました\n");
        }catch(IOException e){
            logArea.appendText("設定の保存中にエラーが発生しました：" + e.getMessage() + "\n");
        }
    }
    private void loadProperties(){
        if(!configFile.exists()){
            logArea.appendText("設定ファイルが見つかりません。\nこのメッセージは初回起動時にも表示されます");
            return;
        }
        try(FileInputStream in = new FileInputStream(configFile)){
            properties.load(in);
            String gameDirectory = properties.getProperty("game_directory");
            if (gameDirectory != null && !gameDirectory.isEmpty()){
                pathInput.setText(gameDirectory);
                pathLabel.setText("ゲームディレクトリ："+ gameDirectory);
                logArea.appendText("設定をconfig.propertiesから読み込みました\n");
            }
        }catch (IOException e){
            logArea.appendText("設定の読み込み中にエラーが発生しました："+ e.getMessage() + "\n");

        }
    }





    /**
     * このJavaプログラムを起動するための、従来通りのmainメソッドです。
     * launch(args)を呼び出すことで、JavaFXのライフサイクルが開始されます。
     */
    public static void main(String[] args) {
        launch(args);
    }
}