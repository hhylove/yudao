package cn.iocoder.yudao.framework.web.config;

import cn.iocoder.yudao.framework.apilog.core.service.ApiErrorLogFrameworkService;
import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.web.core.clean.JsoupXssCleaner;
import cn.iocoder.yudao.framework.web.core.clean.XssCleaner;
import cn.iocoder.yudao.framework.web.core.filter.CacheRequestBodyFilter;
import cn.iocoder.yudao.framework.web.core.filter.DemoFilter;
import cn.iocoder.yudao.framework.web.core.filter.XssFilter;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.handler.GlobalResponseBodyHandler;
import cn.iocoder.yudao.framework.web.core.json.XssStringJsonDeserializer;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import javax.servlet.Filter;

@AutoConfiguration
@EnableConfigurationProperties({WebProperties.class, XssProperties.class})
public class YudaoWebAutoConfiguration implements WebMvcConfigurer {

    @Resource
    private WebProperties webProperties;
    /**
     * ?????????
     */
    @Value("${spring.application.name}")
    private String applicationName;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurePathMatch(configurer, webProperties.getAdminApi());
        configurePathMatch(configurer, webProperties.getAppApi());
    }

    /**
     * ?????? API ????????????????????? controller ?????????
     *
     * @param configurer ??????
     * @param api        API ??????
     */
    private void configurePathMatch(PathMatchConfigurer configurer, WebProperties.Api api) {
        AntPathMatcher antPathMatcher = new AntPathMatcher(".");
        configurer.addPathPrefix(api.getPrefix(), clazz -> clazz.isAnnotationPresent(RestController.class)
                && antPathMatcher.match(api.getController(), clazz.getPackage().getName())); // ???????????? controller ???
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler(ApiErrorLogFrameworkService ApiErrorLogFrameworkService) {
        return new GlobalExceptionHandler(applicationName, ApiErrorLogFrameworkService);
    }

    @Bean
    public GlobalResponseBodyHandler globalResponseBodyHandler() {
        return new GlobalResponseBodyHandler();
    }

    @Bean
    @SuppressWarnings("InstantiationOfUtilityClass")
    public WebFrameworkUtils webFrameworkUtils(WebProperties webProperties) {
        // ?????? WebFrameworkUtils ??????????????? webProperties ?????????????????????????????? Bean
        return new WebFrameworkUtils(webProperties);
    }

    // ========== Filter ?????? ==========

    /**
     * ?????? CorsFilter Bean?????????????????????
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterBean() {
        // ?????? CorsConfiguration ??????
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*"); // ?????????????????????
        config.addAllowedHeader("*"); // ????????????????????????
        config.addAllowedMethod("*"); // ???????????????????????????
        // ?????? UrlBasedCorsConfigurationSource ??????
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // ???????????????????????????
        return createFilterBean(new CorsFilter(source), WebFilterOrderEnum.CORS_FILTER);
    }

    /**
     * ?????? RequestBodyCacheFilter Bean??????????????????????????????
     */
    @Bean
    public FilterRegistrationBean<CacheRequestBodyFilter> requestBodyCacheFilter() {
        return createFilterBean(new CacheRequestBodyFilter(), WebFilterOrderEnum.REQUEST_BODY_CACHE_FILTER);
    }

    /**
     * ?????? XssFilter Bean????????? Xss ????????????
     */
    @Bean
    @ConditionalOnBean(XssCleaner.class)
    public FilterRegistrationBean<XssFilter> xssFilter(XssProperties properties, PathMatcher pathMatcher, XssCleaner xssCleaner) {
        return createFilterBean(new XssFilter(properties, pathMatcher, xssCleaner), WebFilterOrderEnum.XSS_FILTER);
    }

    /**
     * ?????? DemoFilter Bean???????????????
     */
    @Bean
    @ConditionalOnProperty(value = "yudao.demo", havingValue = "true")
    public FilterRegistrationBean<DemoFilter> demoFilter() {
        return createFilterBean(new DemoFilter(), WebFilterOrderEnum.DEMO_FILTER);
    }


    /**
     * Xss ?????????
     *
     * @return XssCleaner
     */
    @Bean
    @ConditionalOnMissingBean(XssCleaner.class)
    public XssCleaner xssCleaner() {
        return new JsoupXssCleaner();
    }

    /**
     * ?????? Jackson ?????????????????????????????? json ??????????????? xss ??????
     *
     * @return Jackson2ObjectMapperBuilderCustomizer
     */
    @Bean
    @ConditionalOnMissingBean(name = "xssJacksonCustomizer")
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnProperty(value = "yudao.xss.enable", havingValue = "true")
    public Jackson2ObjectMapperBuilderCustomizer xssJacksonCustomizer(XssCleaner xssCleaner) {
        // ???????????????????????? xss ??????????????????????????? XssStringJsonSerializer??????????????????????????????
        return builder -> builder.deserializerByType(String.class, new XssStringJsonDeserializer(xssCleaner));
    }

    private static <T extends Filter> FilterRegistrationBean<T> createFilterBean(T filter, Integer order) {
        FilterRegistrationBean<T> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(order);
        return bean;
    }

}
