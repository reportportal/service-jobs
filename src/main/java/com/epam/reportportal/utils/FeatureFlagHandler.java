package com.epam.reportportal.utils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Component for checking enabled feature flags.
 *
 * @author <a href="mailto:ivan_kustau@epam.com">Ivan Kustau</a>
 */
@Component
public class FeatureFlagHandler {

  private final Set<FeatureFlag> enabledFeatureFlagsSet = new HashSet<>();

  /**
   * Initialises {@link FeatureFlagHandler} by environment variable with enabled feature flags.
   *
   * @param featureFlags Set of enabled feature flags
   */
  public FeatureFlagHandler(
      @Value("#{'${rp.feature.flags}'.split(',')}") Set<String> featureFlags) {

    if (!CollectionUtils.isEmpty(featureFlags)) {
      featureFlags.stream().map(FeatureFlag::fromString).filter(Optional::isPresent)
          .map(Optional::get).forEach(enabledFeatureFlagsSet::add);
    }
  }

  public boolean isEnabled(FeatureFlag featureFlag) {
    return enabledFeatureFlagsSet.contains(featureFlag);
  }
}
