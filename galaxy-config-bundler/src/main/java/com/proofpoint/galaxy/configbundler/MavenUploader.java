package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import org.eclipse.jgit.util.Base64;

import java.net.URI;
import java.util.concurrent.Executors;

import static java.lang.String.format;

class MavenUploader
{
    private final URI repositoryUri;
    private final String user;
    private final String password;

    public MavenUploader(URI repositoryUri, String user, String password)
    {
        if (!repositoryUri.toASCIIString().endsWith("/")) {
            repositoryUri = URI.create(repositoryUri.toASCIIString() + "/");
        }

        this.repositoryUri = repositoryUri;
        this.user = user;
        this.password = password;
    }

    public URI upload(String groupId, String artifactId, String version, String type, BodyGenerator writer)
            throws Exception
    {
        HttpClient httpClient = new HttpClient(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("http-%s").setDaemon(true).build()));

        final URI uri = repositoryUri.resolve(format("%s/%s/%s/%s-%s.%s",
                groupId.replace('.', '/'),
                artifactId,
                version,
                artifactId,
                version,
                type));

        Request request = RequestBuilder.preparePut()
                .setUri(uri)
                .addHeader("Authorization", "Basic " + Base64.encodeBytes(format("%s:%s", user, password).getBytes(Charsets.US_ASCII)))
                .setBodyGenerator(writer)
                .build();

        httpClient.execute(request, new ResponseHandler<Object, Exception>()
        {
            public Exception handleException(Request request, Exception exception)
            {
                exception.printStackTrace();
                return exception;
            }

            public Object handle(Request request, Response response)
                    throws Exception
            {
                if (response.getStatusCode() != 201) {
                    throw new RuntimeException(format("Error uploading to %s. Response code: %s", uri.toASCIIString(), response.getStatusCode()));
                }

                return null;
            }
        }).get();

        return uri;
    }
}
