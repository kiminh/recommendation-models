package io.yaochi.recommendation.model.encoder

import com.intel.analytics.bigdl.nn.{Linear, ReLU, Reshape, Sequential}
import com.intel.analytics.bigdl.tensor.Tensor
import io.yaochi.recommendation.util.{BackwardUtil, LayerUtil}

import scala.collection.mutable.ArrayBuffer

class HigherOrderEncoder(batchSize: Int,
                         inputDim: Int,
                         fcDims: Array[Int],
                         mats: Array[Float],
                         start: Int = 0,
                         reshape: Boolean = true) {
  private val (linearLayers, outputLinearLayer) = buildLinearLayers()
  private val module = buildModule()

  def forward(input: Tensor[Float]): Tensor[Float] = {
    module.forward(input).toTensor
  }

  def backward(input: Tensor[Float], gradOutput: Tensor[Float]): Tensor[Float] = {
    val gradTensor = module.backward(input, gradOutput).toTensor[Float]
    var curOffset = start
    for (linearLayer <- linearLayers) {
      curOffset = BackwardUtil.linearBackward(linearLayer, mats, curOffset)
    }

    BackwardUtil.linearBackward(outputLinearLayer, mats, curOffset)

    gradTensor
  }

  private def buildModule(): Sequential[Float] = {
    val encoder = Sequential[Float]()
    if (reshape) {
      encoder.add(Reshape(Array(batchSize, inputDim), Some(false)))
    }

    for (linearLayer <- linearLayers) {
      encoder.add(linearLayer)
      encoder.add(ReLU())
    }
    encoder.add(outputLinearLayer)
    encoder
  }

  private def buildLinearLayers(): (Array[Linear[Float]], Linear[Float]) = {
    val layers = ArrayBuffer[Linear[Float]]()
    var curOffset = start
    var dim = inputDim
    for (fcDim <- fcDims) {
      layers += LayerUtil.buildLinear(dim, fcDim, mats, true, curOffset)
      curOffset += dim * fcDim + fcDim
      dim = fcDim
    }
    (layers.toArray, LayerUtil.buildLinear(dim, 1, mats, true, curOffset))
  }
}

object HigherOrderEncoder {
  def apply(batchSize: Int,
            inputDim: Int,
            fcDims: Array[Int],
            mats: Array[Float],
            start: Int = 0,
            reshape: Boolean = true): HigherOrderEncoder =
    new HigherOrderEncoder(batchSize, inputDim, fcDims, mats, start, reshape)
}
