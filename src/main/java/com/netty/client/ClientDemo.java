package com.netty.client;

import com.squareup.okhttp.*;
import java.io.File;
import java.io.IOException;

/**
 * @author guandezhi
 * @ClassName: com.netty.client
 * @Description:
 * @date 2018/4/11 13:40
 */
public class ClientDemo implements Runnable{

    private static final String IMGUR_CLIENT_ID = "...";
    private static final OkHttpClient client = new OkHttpClient();


    public static void main(String[] args) {
        try {
            ClientDemo d1 = new ClientDemo();
            ClientDemo d2 = new ClientDemo();
            ClientDemo d3 = new ClientDemo();

            new Thread(d1).start();
            new Thread(d2).start();
            new Thread(d3).start();

            d1.upload("F:\\1.txt");
            d2.upload("F:\\2.txt");
            d3.upload("F:\\3.txt");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void upload(String filePath) throws Exception {
        final String FORM_ACTION = "http://127.0.0.1:12345/hsserv/upload";//文件上传路径
        final File file = new File(filePath);//要上传的文件

        String fileName = file.getName();
        MediaType mediaType = MediaType.parse(contentType(file.getAbsolutePath()));

        // Use the imgur image upload API as documented at https://api.imgur.com/endpoints/image
        RequestBody requestBody = new MultipartBuilder()
                .type(MultipartBuilder.FORM)
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"title\""),
                        RequestBody.create(null, "Square Logo"))
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"txt\";filename=\""+fileName+"\""),
                        RequestBody.create(mediaType, file))
                .build();

        Request request = new Request.Builder()
                .header("Authorization", "Client-ID " + IMGUR_CLIENT_ID)
                .header("Connection", "Close")
                .url(FORM_ACTION)
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        System.out.println(response.body().string());
    }

    private String contentType(String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg")) return "image/jpeg";
        if (path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    @Override
    public void run() {
        // do nothing
    }
}
