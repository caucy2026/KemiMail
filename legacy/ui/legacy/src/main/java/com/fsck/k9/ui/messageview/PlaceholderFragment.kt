package com.fsck.k9.ui.messageview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.fsck.k9.ui.R

class PlaceholderFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.empty_message_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messageResId = arguments?.getInt(ARG_MESSAGE_RES_ID)?.takeIf { it != 0 } ?: return
        view.findViewById<TextView>(R.id.message_view_empty_text).setText(messageResId)
    }

    companion object {
        private const val ARG_MESSAGE_RES_ID = "messageResId"

        fun newInstance(@StringRes messageResId: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_MESSAGE_RES_ID, messageResId)
                }
            }
        }
    }
}
