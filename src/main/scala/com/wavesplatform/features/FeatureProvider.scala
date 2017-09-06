package com.wavesplatform.features

import com.wavesplatform.settings.FunctionalitySettings

trait FeatureProvider {
  def approvedFeatures(): Map[Short, Int]
  def activatedFeatures(): Map[Short, Int]
  def featureVotes(height: Int): Map[Short, Int]
}

case class FeaturesProperties(functionalitySettings: FunctionalitySettings) {
  def featureCheckBlocksPeriodAtHeight(height: Int): Int =
    doubleValueAtHeight(height, functionalitySettings.featureCheckBlocksPeriod)

  def blocksForFeatureActivationAtHeight(height: Int): Int =
    doubleValueAtHeight(height, functionalitySettings.blocksForFeatureActivation)

  private def doubleValueAtHeight(height: Int, value: Int): Int =
    if (height > functionalitySettings.doubleFeaturesPeriodsAfterHeight) value * 2 else value
}

object FeatureProvider {

  implicit class FeatureProviderExt(provider: FeatureProvider) {
    def isFeatureActivated(feature: BlockchainFeature, height: Int): Boolean =
      provider.activatedFeatures().get(feature.id).exists(_ <= height)

    def activatedFeatures(height: Int): Set[Short] = provider.activatedFeatures().collect {
      case (featureId, activationHeight) if height >= activationHeight => featureId
    }.toSet

    def featureStatus(feature: Short, height: Int): BlockchainFeatureStatus =
      if (provider.activatedFeatures().get(feature).exists(_ <= height)) BlockchainFeatureStatus.Activated
      else if (provider.approvedFeatures().contains(feature)) BlockchainFeatureStatus.Approved
      else BlockchainFeatureStatus.Undefined

    def featureActivationHeight(feature: Short): Option[Int] = provider.activatedFeatures().get(feature)
    def featureApprovalHeight(feature: Short): Option[Int] = provider.approvedFeatures().get(feature)
  }
}
