/*
 * Copyright (c) 2023. PortSwigger Ltd. All rights reserved.
 *
 * This code may be used to extend the functionality of Burp Suite Community Edition
 * and Burp Suite Professional, provided that this usage does not violate the
 * license terms for those products.
 */

package netspi.protobufDecoder;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.utilities.Base64EncodingOptions;
import burp.api.montoya.utilities.Base64Utils;
import burp.api.montoya.utilities.URLUtils;

import java.awt.*;
import java.io.IOException;
import java.util.Optional;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import static burp.api.montoya.core.ByteArray.byteArray;

class protobufDataEditor implements ExtensionProvidedHttpRequestEditor
{
    private final RawEditor requestEditor;
    private final Base64Utils base64Utils;
    private final URLUtils urlUtils;
    private HttpRequestResponse requestResponse;
    private final MontoyaApi api;

    private enum Command {
        REASSEMBLE_TO_BINARYPB,
        DISASSEMBLE,
        DECODE_WITH_SCHEMA
    }

    private ParsedHttpParameter parsedHttpParameter;

    protobufDataEditor(MontoyaApi api, EditorCreationContext creationContext)
    {
        this.api = api;
        base64Utils = api.utilities().base64Utils();
        urlUtils = api.utilities().urlUtils();

        if (creationContext.editorMode() == EditorMode.READ_ONLY)
        {
            requestEditor = api.userInterface().createRawEditor(EditorOptions.READ_ONLY);
        }
        else {
            requestEditor = api.userInterface().createRawEditor();
        }
    }

    @Override
    public HttpRequest getRequest()
    {
        HttpRequest request;

        if (requestEditor.isModified())
        {
            String b64PB = getOutput(String.valueOf(requestEditor.getContents()), "protoscope -s | base64");
            String encodedData = urlUtils.encode(b64PB);

            request = requestResponse.request().withUpdatedParameters(HttpParameter.parameter(parsedHttpParameter.name(), encodedData, parsedHttpParameter.type()));
        }
        else
        {
            request = requestResponse.request();
        }

        return request;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse)
    {
        this.requestResponse = requestResponse;
        String b64Encoded = urlUtils.decode(parsedHttpParameter.value());
        String stringPB = String.valueOf(base64Utils.decode(b64Encoded));
        String output = getOutput(stringPB, "protoscope");

        this.requestEditor.setContents(ByteArray.byteArray(output.getBytes()));
    }

    private String getOutput(String stringPB, String command) {

        String userHomePath = System.getProperty("user.home");
        String b64EncodedPB = base64Utils.encodeToString(stringPB);
        String[] cmd = {
                "/bin/bash",
                "-c",
                String.format("export PATH=\"%s/go/bin/:$PATH\" && echo %s | base64 -d | %s ", userHomePath,b64EncodedPB, command)
        };

        StringBuilder output = new StringBuilder();

        try {

            api.logging().logToOutput(String.format("cmd ran: %s",String.join(",",cmd)));

            Process proc = new ProcessBuilder(cmd).start();

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(proc.getErrorStream()));
            if (stdError.ready()) {
                api.logging().logToOutput(stdError.toString());
            }
            String s = null;

            while ((s = stdInput.readLine()) != null) {
                output.append(s != null ? s : "").append(System.getProperty("line.separator"));
            }

        } catch (IOException e) {
            api.logging().logToOutput(e.toString());
            throw new RuntimeException(e);
        }
        return output.toString().trim();
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse)
    {
        Optional<ParsedHttpParameter> dataParam = requestResponse.request().parameters().stream().filter(p -> p.name().equals("data_binary") || p.name().equals("request_binary")).findFirst();

        dataParam.ifPresent(httpParameter -> parsedHttpParameter = httpParameter);

        return dataParam.isPresent();

    }

    @Override
    public String caption()
    {
        return "Protobuf Editor";
    }

    @Override
    public Component uiComponent()
    {
        return requestEditor.uiComponent();
    }

    @Override
    public Selection selectedData()
    {
        return requestEditor.selection().isPresent() ? requestEditor.selection().get() : null;
    }

    @Override
    public boolean isModified()
    {
        return requestEditor.isModified();
    }
}