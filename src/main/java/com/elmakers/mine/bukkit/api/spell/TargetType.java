package com.elmakers.mine.bukkit.api.spell;

/**
 * The type of targeting this Spell should perform.
 * 
 * This driven by the "target" parameter in the spell configuration or
 * command-line parameters.
 * 
 */
public enum TargetType {
    NONE,
    BLOCK,
    ANY,
    OTHER,
    ANY_ENTITY,
    OTHER_ENTITY,
    SELF,
    SELECT
}
