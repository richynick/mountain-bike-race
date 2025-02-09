package com.richard.weather.service;


import com.richard.weather.model.FormatResponse;
import com.richard.weather.model.Weather;
import com.richard.weather.model.WeatherDetails;
import com.richard.weather.repository.OwmRepository;
import com.richard.weather.utility.Constant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

@Service("/owmService")
public class WeatherServiceImpl implements WeatherService {

    private static final Logger Log = LoggerFactory.getLogger(WeatherServiceImpl.class);

    private final OWMAddress owmAddress;
    private final OWMResponse owmResponse;
    private final FormatResponse formatResponse;

    @Autowired
    OwmRepository owmRepository;

    public WeatherServiceImpl() {
        this.owmAddress = new OWMAddress();
        this.owmResponse = new OWMResponse();
        this.formatResponse = new FormatResponse();
    }

    @Value("${api.key}")
    private String apiKey;

    public JSONObject getWeatherDetailsByCityName(String cityName){
        String response = owmResponse.httpGETResponseFromOWM(
                owmAddress.getOwmAddressUrl(Constant.URL_WEATHER, Constant.PARAM_CITY_NAME, cityName, apiKey));

        WeatherDetails weatherDetails = new WeatherDetails(new JSONObject(response));

        return saveAndRetrieveResponse(weatherDetails);
    }

    public JSONArray getWeatherDetailsByCityId(String city_Id, String appId){
        String response = owmResponse.httpGETResponseFromOWM(
                owmAddress.getOwmAddressUrl(Constant.URL_GROUP, Constant.PARAM_CITY_ID, city_Id, appId));

        JSONArray jsonArrayResp = new JSONArray();
        JSONArray currentWeatherArray = new JSONObject(response).optJSONArray(Constant.JSON_LIST);

        if (currentWeatherArray != null && currentWeatherArray != Collections.emptyList()) {
            for(int i = 0; i < currentWeatherArray.length(); i++) {

                JSONObject weatherObj = currentWeatherArray.optJSONObject(i);
                WeatherDetails weatherDetails = new WeatherDetails(weatherObj);

                jsonArrayResp.put(saveAndRetrieveResponse(weatherDetails));
            }
        }

        return jsonArrayResp;
    }

    private JSONObject saveAndRetrieveResponse(WeatherDetails weatherDetails) {

        Weather weatherObj = new Weather();

        weatherObj.setCityId(weatherDetails.getCityId());
        weatherObj.setCityName(weatherDetails.getCityName());
        weatherObj.setWeather(formatResponse.formatWeatherListToString(weatherDetails));
        weatherObj.setTemp(weatherDetails.getMainInstance().getTemp());

        owmRepository.save(weatherObj);

        return formatResponse.formatJSONResponse(weatherObj);
    }


    private class OWMAddress {

        private String getOwmAddressUrl(String type, String param, String cityId, String appId) {
            String owmAddressUrl = new StringBuilder()
                    .append(Constant.URL_API).append(type).append(param).append(cityId)
                    .append(Constant.PARAM_APP_ID).append(appId).toString();
            Log.info(owmAddressUrl);
            return owmAddressUrl;
        }

    }

    private class OWMResponse {

        private String httpGETResponseFromOWM(String requestAddress) {

            URL urlRequest;
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            String tmpStr;
            String response = null;

            try {
                urlRequest = new URL(requestAddress);

                connection = (HttpURLConnection) urlRequest.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.setDoOutput(false);
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {

                    String encoding = connection.getContentEncoding();

                    try {
                        if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(connection.getInputStream())));
                        } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                            reader = new BufferedReader(new InputStreamReader(new InflaterInputStream(connection.getInputStream(), new Inflater(true))));
                        } else {
                            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        }

                        while ((tmpStr = reader.readLine()) != null) {
                            response = tmpStr;
                        }
                    }  catch (IOException e) {
                        Log.error(e.getMessage());
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                Log.error(e.getMessage());
                            }
                        }
                    }

                } else {
                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        while ((tmpStr = reader.readLine()) != null) {
                            response = tmpStr;
                        }
                    } catch (IOException e) {
                        Log.error(e.getMessage());
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                Log.error(e.getMessage());
                            }
                        }
                    }
                }

            } catch (IOException e) {
                Log.error(e.getMessage());
                response = null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            return response;
        }
    }

}
