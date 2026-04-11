#include <obs-module.h>
#include "camari-source.h"

OBS_DECLARE_MODULE()
OBS_MODULE_USE_DEFAULT_LOCALE("camari-obs-plugin", "en-US")

MODULE_EXPORT const char *obs_module_description(void)
{
    return "Camari — stream your Android phone camera directly into OBS";
}

bool obs_module_load(void)
{
    obs_register_source(&camari_source_info);
    return true;
}

void obs_module_unload(void)
{
    /* nothing to clean up at module level */
}
