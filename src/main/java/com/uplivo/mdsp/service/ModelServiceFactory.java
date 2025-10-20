package com.uplivo.mdsp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 模型服务工厂：根据版本号动态获取对应的模型服务实例
 * @Author charles
 * @Date 2025/9/8 14:02
 * @Version 1.0.0
 */
@Slf4j
@Component
public class ModelServiceFactory {

    private final Map<String, AbstractModelService> serviceMap = new HashMap<>();

    @Autowired
    public ModelServiceFactory(List<AbstractModelService> modelServices) {
        for (AbstractModelService service : modelServices) {
            String version = service.getModelVersion();
            serviceMap.put(version, service);
            log.info("Model service registered - version: {}, service: {}", version, service.getClass().getSimpleName());
        }
        log.info("Total model services registered: {}", serviceMap.size());
    }

    public AbstractModelService getServiceByVersion(String version) {
        AbstractModelService service = serviceMap.get(version);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported model version: " + version
                    + ", supported versions: " + serviceMap.keySet());
        }
        return service;
    }
}
