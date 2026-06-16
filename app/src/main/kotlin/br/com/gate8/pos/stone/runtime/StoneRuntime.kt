package br.com.gate8.pos.stone.runtime

/**
 * Bootstrap do flavor Stone — no-op no mock.
 */
interface StoneRuntime {
    fun onApplicationStart()
}

class NoOpStoneRuntime : StoneRuntime {
    override fun onApplicationStart() = Unit
}
