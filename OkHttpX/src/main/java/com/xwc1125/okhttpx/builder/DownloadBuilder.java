package com.xwc1125.okhttpx.builder;

import com.xwc1125.okhttpx.OkHttpX;
import com.xwc1125.okhttpx.body.ResponseProgressBody;
import com.xwc1125.okhttpx.callback.DownloadCallback;
import com.xwc1125.okhttpx.response.DownloadResponseHandler;
import com.xwc1125.okhttpx.util.LogUtils;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * download builder
 *
 * @author tsy
 * @date 2016/12/6
 */
public class DownloadBuilder {

    private OkHttpX mOkHttpX;

    private String mUrl = "";
    private Object mTag;
    private Map<String, String> mHeaders;

    /**
     * 文件目录
     */
    private String mFileDir = "";
    /**
     * 文件名
     */
    private String mFileName = "";
    /**
     * 文件路径 （如果设置该字段则上面2个就不需要）
     */
    private String mFilePath = "";

    /**
     * 已经完成的字节数 用于断点续传
     */
    private Long mCompleteBytes = 0L;

    public DownloadBuilder(OkHttpX okHttpX) {
        mOkHttpX = okHttpX;
    }

    public DownloadBuilder url(String url) {
        this.mUrl = url;
        return this;
    }

    /**
     * set file storage dir
     *
     * @param fileDir file directory
     * @return
     */
    public DownloadBuilder fileDir(String fileDir) {
        this.mFileDir = fileDir;
        return this;
    }

    /**
     * set file storage name
     *
     * @param fileName file name
     * @return
     */
    public DownloadBuilder fileName(String fileName) {
        this.mFileName = fileName;
        return this;
    }

    /**
     * set file path
     *
     * @param filePath file path
     * @return
     */
    public DownloadBuilder filePath(String filePath) {
        this.mFilePath = filePath;
        return this;
    }

    /**
     * set tag
     *
     * @param tag tag
     * @return
     */
    public DownloadBuilder tag(Object tag) {
        this.mTag = tag;
        return this;
    }

    /**
     * set headers
     *
     * @param headers headers
     * @return
     */
    public DownloadBuilder headers(Map<String, String> headers) {
        this.mHeaders = headers;
        return this;
    }

    /**
     * set one header
     *
     * @param key header key
     * @param val header val
     * @return
     */
    public DownloadBuilder addHeader(String key, String val) {
        if (this.mHeaders == null) {
            mHeaders = new LinkedHashMap<>();
        }
        mHeaders.put(key, val);
        return this;
    }

    /**
     * set completed bytes (BreakPoints)
     *
     * @param completeBytes 已经完成的字节数
     * @return
     */
    public DownloadBuilder setCompleteBytes(Long completeBytes) {
        if (completeBytes > 0L) {
            this.mCompleteBytes = completeBytes;
            //添加断点续传header
            addHeader("RANGE", "bytes=" + completeBytes + "-");
        }
        return this;
    }

    /**
     * 异步执行
     *
     * @param downloadResponseHandler 下载回调
     */
    public Call enqueue(final DownloadResponseHandler downloadResponseHandler) {
        try {
            if (mUrl.length() == 0) {
                throw new IllegalArgumentException("Url can not be null !");
            }

            if (mFilePath.length() == 0) {
                if (mFileDir.length() == 0 || mFileName.length() == 0) {
                    throw new IllegalArgumentException("FilePath can not be null !");
                } else {
                    mFilePath = mFileDir + mFileName;
                }
            }
            checkFilePath(mFilePath, mCompleteBytes);

            Request.Builder builder = new Request.Builder().url(mUrl);
            appendHeaders(builder, mHeaders);

            if (mTag != null) {
                builder.tag(mTag);
            }

            Request request = builder.build();

            Call call = mOkHttpX.getOkHttpClient().newBuilder()
                    // 设置拦截器
                    .addNetworkInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Response originalResponse = chain.proceed(chain.request());
                            return originalResponse.newBuilder()
                                    .body(new ResponseProgressBody(originalResponse.body(), downloadResponseHandler))
                                    .build();
                        }
                    })
                    .build()
                    .newCall(request);
            call.enqueue(new DownloadCallback(downloadResponseHandler, mFilePath, mCompleteBytes));

            return call;
        } catch (Exception e) {
            LogUtils.e("Download enqueue error:" + e.getMessage());
            downloadResponseHandler.onFailure(e.getMessage());
            return null;
        }
    }

    /**
     * 检查filePath有效性
     *
     * @param filePath
     * @param completeBytes
     * @throws Exception
     */
    private void checkFilePath(String filePath, Long completeBytes) throws Exception {
        File file = new File(filePath);
        if (file.exists()) {
            return;
        }

        if (completeBytes > 0L) {
            //如果设置了断点续传 则必须文件存在
            throw new Exception("no exist the filePath: " + filePath);
        }

        if (filePath.endsWith(File.separator)) {
            throw new Exception("create file error, the filePat can not dir. filePath: " + filePath);
        }

        //判断目标文件所在的目录是否存在
        if (!file.getParentFile().exists()) {
            if (!file.getParentFile().mkdirs()) {
                throw new Exception("create dir err");
            }
        }
    }

    /**
     * append headers into builder
     *
     * @param builder
     * @param headers
     */
    private void appendHeaders(Request.Builder builder, Map<String, String> headers) {
        Headers.Builder headerBuilder = new Headers.Builder();
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (String key : headers.keySet()) {
            headerBuilder.add(key, headers.get(key));
        }
        builder.headers(headerBuilder.build());
    }
}
