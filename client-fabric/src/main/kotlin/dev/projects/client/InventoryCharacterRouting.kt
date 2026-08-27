package dev.projects.client

fun shouldOpenInventoryCharacterScreen(
    projectSProtocolSessionActive: Boolean,
    screenOpen: Boolean,
    infiniteMaterials: Boolean,
    serverControlledInventory: Boolean,
): Boolean = projectSProtocolSessionActive && !screenOpen && !infiniteMaterials && !serverControlledInventory
