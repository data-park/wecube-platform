package com.webank.wecube.platform.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.domain.SystemVariable;
import com.webank.wecube.platform.core.dto.QueryRequest;
import com.webank.wecube.platform.core.dto.QueryResponse;
import com.webank.wecube.platform.core.dto.SystemVariableDto;
import com.webank.wecube.platform.core.jpa.EntityRepository;
import com.webank.wecube.platform.core.jpa.SystemVariableRepository;
import com.webank.wecube.platform.core.utils.StringUtils;

@Service
@Transactional
public class SystemVariableService {
    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private SystemVariableRepository systemVariableRepository;

    public QueryResponse<SystemVariableDto> retrieveSystemVariables(QueryRequest queryRequest) {
        QueryResponse<SystemVariable> queryResponse = entityRepository.query(SystemVariable.class, queryRequest);
        List<SystemVariableDto> systemVariableDto = Lists.transform(queryResponse.getContents(),
                x -> SystemVariableDto.fromDomain(x));
        return new QueryResponse<>(queryResponse.getPageInfo(), systemVariableDto);
    }

    @Transactional
    public List<SystemVariableDto> createSystemVariables(List<SystemVariableDto> resourceSystemVariables) {
        Iterable<SystemVariable> savedDomains = systemVariableRepository
                .saveAll(convertVariableDtoToDomain(resourceSystemVariables));
        return convertVariableDomainToDto(savedDomains);
    }

    @Transactional
    public List<SystemVariableDto> updateSystemVariables(List<SystemVariableDto> resourceSystemVariables) {
        Iterable<SystemVariable> savedDomains = systemVariableRepository
                .saveAll(convertVariableDtoToDomain(resourceSystemVariables));
        return convertVariableDomainToDto(savedDomains);
    }

    @Transactional
    public void deleteSystemVariables(List<SystemVariableDto> systemVariableDtos) {
        validateIfSystemVariablesAreExists(systemVariableDtos);
        systemVariableRepository.deleteAll(convertVariableDtoToDomain(systemVariableDtos));
    }

    private void validateIfSystemVariablesAreExists(List<SystemVariableDto> systemVariableDtos) {
        systemVariableDtos.forEach(dto -> {
            if (dto.getId() == null && !systemVariableRepository.existsById(dto.getId())) {
                throw new WecubeCoreException(String.format("Can not find variable with id [%s].", dto.getId()));
            }
        });
    }

    private List<SystemVariable> convertVariableDtoToDomain(List<SystemVariableDto> systemVariableDtos) {
        List<SystemVariable> domains = new ArrayList<>();
        systemVariableDtos.forEach(dto -> {
            SystemVariable existedServer = null;
            if (dto.getId() != null) {
                Optional<SystemVariable> existedSystemVariableOpt = systemVariableRepository.findById(dto.getId());
                if (existedSystemVariableOpt.isPresent()) {
                    existedServer = existedSystemVariableOpt.get();
                }
            }
            SystemVariable domain = SystemVariableDto.toDomain(dto, existedServer);
            domains.add(domain);
        });
        return domains;
    }

    private List<SystemVariableDto> convertVariableDomainToDto(Iterable<SystemVariable> savedDomains) {
        List<SystemVariableDto> dtos = new ArrayList<>();
        savedDomains.forEach(domain -> dtos.add(SystemVariableDto.fromDomain(domain)));
        return dtos;
    }

    public SystemVariable getSystemVariableById(String varId) {
        Optional<SystemVariable> systemVariable = systemVariableRepository.findById(varId);
        if (systemVariable.isPresent()) {
            return systemVariable.get();
        } else {
            throw new WecubeCoreException("System Variable not found for id: " + varId);
        }
    }

    public List<SystemVariable> getPluginSystemVariableByPackageIdAndName(Integer packageId, String varName) {
        return systemVariableRepository.findAllByPluginPackageIdAndNameAndScopeTypeAndStatus(packageId, varName,
                SystemVariable.SCOPE_TYPE_PLUGIN_PACKAGE, SystemVariable.ACTIVE);
    }

    public List<SystemVariable> getGlobalSystemVariableByName(String varName) {
        return systemVariableRepository.findByNameAndScopeTypeAndStatus(varName, SystemVariable.SCOPE_TYPE_GLOBAL,
                SystemVariable.ACTIVE);
    }

    public String variableReplacement(Integer packageId, String originalString) {
        List<String> varList = StringUtils.findSystemVariableString(originalString);
        for (int i = 0; i < varList.size(); i++) {
            String varString = varList.get(i);
            String varName = varString.substring(2, varString.length() - 2);
            List<SystemVariable> varObjects = getPluginSystemVariableByPackageIdAndName(packageId, varName);
            if (varObjects.size() == 0) {
                varObjects = getGlobalSystemVariableByName(varName);
                if (varObjects.size() == 0) {
                    throw new WecubeCoreException(String.format("Can not found system variable[%s]", varName));
                }
            }
            SystemVariable varObject = varObjects.get(0);
            String varValue = varObject.getValue() == null || varObject.getValue().isEmpty()
                    ? varObject.getDefaultValue()
                    : varObject.getValue();
            originalString = originalString.replace(varString, varValue);
        }
        return originalString;
    }

    public Iterable<SystemVariable> getAllSystemVariable() {
        return systemVariableRepository.findAll();
    }
}
