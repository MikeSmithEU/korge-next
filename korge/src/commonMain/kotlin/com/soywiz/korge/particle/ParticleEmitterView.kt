package com.soywiz.korge.particle

import com.soywiz.kds.iterators.*
import com.soywiz.klock.*
import com.soywiz.korag.*
import com.soywiz.korge.debug.*
import com.soywiz.korge.render.*
import com.soywiz.korge.time.*
import com.soywiz.korge.view.*
import com.soywiz.korio.util.*
import com.soywiz.korma.geom.*
import kotlin.math.*

inline fun Container.particleEmitter(
	emitter: ParticleEmitter, emitterPos: IPoint = IPoint(),
    time: TimeSpan = TimeSpan.NIL,
	callback: ParticleEmitterView.() -> Unit = {}
) = ParticleEmitterView(emitter, emitterPos).apply { this.timeUntilStop = time }.addTo(this, callback)

suspend fun Container.attachParticleAndWait(
    particle: ParticleEmitter,
    x: Double,
    y: Double,
    time: TimeSpan = TimeSpan.NIL,
    speed: Double = 1.0
) {
    val p = particle.create(x, y, time)
    p.speed = speed
    this += p
    p.waitComplete()
    this -= p
}

class ParticleEmitterView(val emitter: ParticleEmitter, emitterPos: IPoint = IPoint()) : View(), KorgeDebugNode {
	val simulator = ParticleEmitterSimulator(emitter, emitterPos)

	var timeUntilStop by simulator::timeUntilStop.redirected()
	val emitterPos by simulator::emitterPos.redirected()
	var emitting by simulator::emitting.redirected()
	val aliveCount by simulator::aliveCount.redirected()
	val anyAlive by simulator::anyAlive.redirected()

	init {
		addUpdater { dt ->
			simulator.simulate(dt)
		}
	}

    fun restart() {
        simulator.restart()
    }

	suspend fun waitComplete() {
		while (anyAlive) waitFrame()
	}

    private var cachedBlending = AG.Blending.NORMAL

	// @TODO: Make ultra-fast rendering flushing ctx and using a custom shader + vertices + indices
	override fun renderInternal(ctx: RenderContext) {
		if (!visible) return
		//ctx.flush()

        if (cachedBlending.srcRGB != emitter.blendFuncSource || cachedBlending.dstRGB != emitter.blendFuncDestination) {
            cachedBlending = AG.Blending(emitter.blendFuncSource, emitter.blendFuncDestination)
        }

		val context = ctx.ctx2d
		val texture = emitter.texture ?: return
		val cx = texture.width * 0.5
		val cy = texture.height * 0.5
		context.keep {
			context.blendFactors = cachedBlending
			context.setMatrix(globalMatrix)

			simulator.particles.fastForEach { p ->
                if (p.alive) {
                    val scale = p.scale
                    context.multiplyColor = p.color
                    context.imageScale(ctx.getTex(texture), p.x - cx * scale, p.y - cy * scale, scale)
                }
			}
		}
	}

    override fun getDebugProperties(): EditableNode {
        val particle = this.emitter
        return EditableNodeList {
            add(EditableSection("Emitter Type", particle::emitterType.toEditableProperty(ParticleEmitter.Type.values())))
            add(EditableSection("Blend Factors", particle::blendFuncSource.toEditableProperty(AG.BlendFactor.values()), particle::blendFuncDestination.toEditableProperty(AG.BlendFactor.values())))
            add(EditableSection("Angle",
                particle::angle.toEditableProperty(0.0, 360.0, 0.0, PI * 2),
                particle::angleVariance.toEditableProperty(0.0, 360.0, 0.0, PI * 2)
            ))
            add(EditableSection("Speed",
                particle::speed.toEditableProperty(0.0, 1000.0),
                particle::speedVariance.toEditableProperty(0.0, 1000.0),
            ))
            add(EditableSection("Lifespan",
                particle::lifeSpan.toEditableProperty(0.0, 10.0),
                particle::lifespanVariance.toEditableProperty(-10.0, 10.0),
                particle::duration.toEditableProperty(-10.0, 10.0),
            ))
            add(EditableSection("Gravity", particle.gravity.editableNodes()))
            add(EditableSection("Source Position", particle.sourcePosition.editableNodes()))
            add(EditableSection("Source Position Variance", particle.sourcePositionVariance.editableNodes()))
            add(EditableSection("Acceleration",
                particle::radialAcceleration.toEditableProperty(-1000.0, +1000.0),
                particle::radialAccelVariance.toEditableProperty(-1000.0, +1000.0),
                particle::tangentialAcceleration.toEditableProperty(-1000.0, +1000.0),
                particle::tangentialAccelVariance.toEditableProperty(-1000.0, +1000.0)
            ))
            add(EditableSection("Start Color", particle.startColor.editableNodes()))
            add(EditableSection("Start Color Variance", particle.startColorVariance.editableNodes(variance = true)))
            add(EditableSection("End Color", particle.endColor.editableNodes()))
            add(EditableSection("End Color Variance", particle.endColor.editableNodes(variance = true)))
            add(EditableSection("Max particles", particle::maxParticles.toEditableProperty(1, 20000)))
            add(EditableSection("Start Size", particle::startSize.toEditableProperty(1.0, 1000.0), particle::startSizeVariance.toEditableProperty(-1000.0, 1000.0)))
            add(EditableSection("End Size", particle::endSize.toEditableProperty(1.0, 1000.0), particle::endSizeVariance.toEditableProperty(-1000.0, 1000.0)))
            add(EditableSection("Radius",
                particle::minRadius.toEditableProperty(0.0, 1000.0),
                particle::minRadiusVariance.toEditableProperty(-1000.0, 1000.0),
                particle::maxRadius.toEditableProperty(0.0, 1000.0),
                particle::maxRadiusVariance.toEditableProperty(-1000.0, 1000.0),
            ))
            add(EditableSection("Rotate",
                particle::rotatePerSecond.toEditableProperty(0.0, 1000.0),
                particle::rotatePerSecondVariance.toEditableProperty(-1000.0, 1000.0),
                particle::rotationStart.toEditableProperty(0.0, 1000.0),
                particle::rotationStartVariance.toEditableProperty(-1000.0, 1000.0),
                particle::rotationEnd.toEditableProperty(0.0, 1000.0),
                particle::rotationEndVariance.toEditableProperty(-1000.0, 1000.0),
            ))
        }
    }
}
