package com.outsmartis.yoke.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.outsmartis.yoke.helper.isEinkDisplay
import com.outsmartis.yoke.helper.isSystemAnimationsDisabled
import com.outsmartis.yoke.theme.ThemeApplier

open class BaseFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ThemeApplier.apply(view, surface = true)
    }

    override fun onCreateAnimator(transit: Int, enter: Boolean, nextAnim: Int): Animator? {
        if (nextAnim != 0 && (requireContext().isSystemAnimationsDisabled() || requireContext().isEinkDisplay()))
            return ValueAnimator.ofFloat(0f, 1f).setDuration(0)
        return null
    }
}
