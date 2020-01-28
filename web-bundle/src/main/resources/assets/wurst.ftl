<#-- @ftlvariable name="" type="com.graphhopper.resources.ConditionalTurnRestrictionsResource.ConditionalTurnRestrictionsView" -->
<!DOCTYPE html>
<html>
<#setting number_format="0.#####">
<ul>
    <#list restrictions as restriction>
        <li>
                <a href="/maps/?point=${restriction.node.lat},${restriction.node.lon}&point=${restriction.node.lat},${restriction.node.lon}&vehicle=car&weighting=fastest">${restriction.osmid}</a><br/>
                <ul>
                <#list restriction.restrictionData as conditionalTagData>
                        <li>
                                ${conditionalTagData.tag}
                                <ul>
                                <#list conditionalTagData.restrictionData as timeDependentRestrictionData>
                                        <li>
                                                ${timeDependentRestrictionData.restriction}
                                                <ul>
                                                <#list timeDependentRestrictionData.rules as rule>
                                                        <li>
                                                                ${rule} <#if matches(rule)><===</#if>
                                                        </li>
                                                </#list>
                                                </ul>
                                        </li>
                                </#list>
                                </ul>
                        </li>
                </#list>
                </ul>
        </li>
    </#list>
</ul>
</html>

