package com.paymentx.prompt.mapper;

import com.paymentx.prompt.dto.PromptVersionResponse;
import com.paymentx.prompt.entity.PromptVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * English:
 * Maps a real PromptVersion entity row to its wire shape,
 * PromptVersionResponse - the same MapStruct
 * (componentModel="spring") convention Routing Service's RouteRuleMapper
 * already uses (Step 28: no new mapping framework introduced).
 * `promptKey` is a constructor/method parameter rather than an entity
 * field (PromptVersion only stores promptTemplateId, a UUID FK - see
 * that entity's javadoc for why there is no JPA association to walk) -
 * every call site already has the key in hand (it came from the
 * request path or the already-loaded PromptTemplate), so passing it
 * through avoids a second database lookup just to populate one response
 * field.
 * Why it exists: keeps PromptServiceImpl free of hand-written
 * field-by-field copying for the parts of the mapping that genuinely
 * are 1:1.
 * How it communicates with other components: called by
 * PromptServiceImpl everywhere a PromptVersion needs to become a
 * PromptVersionResponse (get version, list versions, create version,
 * activate, deactivate, and as the nested activeVersion inside
 * PromptResponse).
 *
 * Hinglish:
 * Ek real PromptVersion entity row ko uske wire shape,
 * PromptVersionResponse, me map karta hai - wahi MapStruct
 * (componentModel="spring") convention jo Routing Service ka
 * RouteRuleMapper already use karta hai (Step 28: koi naya mapping
 * framework introduce nahi kiya gaya). `promptKey` ek entity field nahi
 * balki ek constructor/method parameter hai (PromptVersion sirf
 * promptTemplateId store karta hai, ek UUID FK - us entity ka javadoc
 * dekho ki wahan koi JPA association walk karne layak kyun nahi hai) -
 * har call site ke paas already key haath me hoti hai (wo request path
 * se aayi, ya already-loaded PromptTemplate se) - isliye ise pass karna
 * sirf ek response field populate karne ke liye ek doosra database
 * lookup avoid karta hai.
 * Ye kyu hai: PromptServiceImpl ko us mapping ke un hisso ke liye
 * hand-written field-by-field copying se free rakhta hai jo genuinely
 * 1:1 hain.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * jahan bhi ek PromptVersion ko ek PromptVersionResponse banana hota
 * hai (get version, list versions, create version, activate,
 * deactivate, aur PromptResponse ke andar nested activeVersion ke roop
 * me) wahan ise call karta hai.
 */
@Mapper(componentModel = "spring")
public interface PromptVersionMapper {

    @Mapping(target = "promptKey", source = "promptKey")
    PromptVersionResponse toResponse(PromptVersion version, String promptKey);
}
