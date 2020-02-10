<#-- @ftlvariable name="" type="com.graphhopper.view.ConditionalRestrictionsView" -->
<!DOCTYPE html>
<html>
<#setting number_format="0.#####">
<ul>
    <#list restrictions as restriction>
        <li>
                <a href="/maps/?point=${restriction.from.y},${restriction.from.x}&point=${restriction.to.y},${restriction.to.x}&vehicle=car&weighting=fastest&block_property=conditional&algorithm=astar&ch.disable=true">${restriction.osmid}</a><br/>
                <#if restriction.accessible(linkEnterTime).present>accessible=<#if restriction.accessible(linkEnterTime).get()>yes<#else>no</#if></#if>
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
                                                                ${rule} <#if restriction.matches(linkEnterTime, rule)><===</#if>
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

