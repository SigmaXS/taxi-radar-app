package com.example.taxiradar

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Общий чат водителей Taxi Radar. Новые сообщения подтягиваем раз в 4 секунды,
 * пока экран открыт. Писать можно только с ником; модерация — в админке.
 */
class ChatActivity : AppCompatActivity() {

    private data class Message(
        val id: Long, val nickname: String, val text: String, val ts: Long, val mine: Boolean, val admin: Boolean
    )

    private val messages = mutableListOf<Message>()
    private lateinit var adapter: Adapter
    private lateinit var rv: RecyclerView
    private lateinit var etText: EditText
    private lateinit var tvMe: TextView
    private var nickname: String? = null
    private var muted = false
    private var isAdmin = false
    private var poll: Job? = null
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        rv = findViewById(R.id.rvChat)
        etText = findViewById(R.id.etChat)
        tvMe = findViewById(R.id.tvChatMe)
        adapter = Adapter()
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rv.adapter = adapter

        findViewById<View>(R.id.btnChatBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnChatNick).setOnClickListener { askNickname() }
        findViewById<View>(R.id.btnChatSend).setOnClickListener { send() }
    }

    override fun onResume() {
        super.onResume()
        poll = lifecycleScope.launch {
            while (isActive) {
                load()
                delay(4000)
            }
        }
    }

    override fun onPause() {
        poll?.cancel()
        super.onPause()
    }

    private suspend fun load() {
        val after = messages.lastOrNull()?.id ?: 0L
        val json = CommunityApi.post(this, "/api/chat/list", JSONObject().put("after", after))
        if (json == null) {
            tvMe.text = getString(R.string.clients_no_connection)
            return
        }
        if (!json.optBoolean("ok")) {
            tvMe.text = json.optString("message")
            return
        }
        val me = json.optJSONObject("me")
        nickname = me?.optString("nickname")?.takeIf { it.isNotBlank() }
        muted = me?.optBoolean("muted") == true
        isAdmin = me?.optBoolean("admin") == true
        tvMe.text = when {
            muted -> getString(R.string.chat_muted)
            nickname != null && isAdmin -> getString(R.string.chat_you_are_admin, nickname)
            nickname != null -> getString(R.string.chat_you_are, nickname)
            else -> getString(R.string.chat_no_nick)
        }
        // Сообщения, которые удалил админ, убираем и у себя.
        json.optJSONArray("deleted")?.let { del ->
            val ids = (0 until del.length()).map { del.optLong(it) }.toSet()
            if (messages.removeAll { it.id in ids }) adapter.notifyDataSetChanged()
        }
        val arr = json.optJSONArray("messages") ?: return
        if (arr.length() == 0) return
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val atBottom = !rv.canScrollVertically(1)
        val start = messages.size
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val id = m.getLong("id")
            if (messages.any { it.id == id }) continue
            messages += Message(
                id, m.optString("nickname"), m.optString("text"),
                try { iso.parse(m.optString("ts").take(19))?.time ?: 0L } catch (e: Exception) { 0L },
                m.optBoolean("mine"),
                m.optBoolean("admin")
            )
        }
        adapter.notifyItemRangeInserted(start, messages.size - start)
        if (atBottom || start == 0) rv.scrollToPosition(messages.size - 1)
    }

    private fun send() {
        val text = etText.text.toString().trim()
        if (text.isEmpty() || sending) return
        if (nickname == null) {
            askNickname { send() }
            return
        }
        sending = true
        lifecycleScope.launch {
            val r = CommunityApi.post(this@ChatActivity, "/api/chat/send", JSONObject().put("text", text))
            sending = false
            when {
                r == null -> toast(getString(R.string.clients_no_connection))
                !r.optBoolean("ok") -> toast(r.optString("message"))
                else -> {
                    etText.setText("")
                    load()
                    rv.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun askNickname(then: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.chat_nick_hint)
            setText(nickname.orEmpty())
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(20))
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_nick_title)
            .setView(box)
            .setPositiveButton(R.string.clients_save) { _, _ ->
                lifecycleScope.launch {
                    val r = CommunityApi.post(
                        this@ChatActivity, "/api/chat/profile",
                        JSONObject().put("nickname", input.text.toString())
                    )
                    when {
                        r == null -> toast(getString(R.string.clients_no_connection))
                        !r.optBoolean("ok") -> toast(r.optString("message"))
                        else -> {
                            nickname = r.optString("nickname")
                            tvMe.text = getString(R.string.chat_you_are, nickname)
                            then?.invoke()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Админ чата: долгое нажатие на сообщение — удалить или заглушить автора. */
    private fun moderate(m: Message) {
        val actions = mutableListOf(getString(R.string.chat_mod_delete) to "delete")
        if (!m.mine) actions += getString(R.string.chat_mod_mute, m.nickname) to "mute"
        MaterialAlertDialogBuilder(this)
            .setTitle(m.nickname)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                lifecycleScope.launch {
                    val r = CommunityApi.post(
                        this@ChatActivity, "/api/chat/moderate",
                        JSONObject().put("id", m.id).put("action", actions[which].second)
                    )
                    when {
                        r == null -> toast(getString(R.string.clients_no_connection))
                        !r.optBoolean("ok") -> toast(r.optString("message"))
                        else -> {
                            messages.removeAll { it.id == m.id || (actions[which].second == "mute" && it.nickname == m.nickname && !it.mine) }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val bubble: LinearLayout = v.findViewById(R.id.bubble)
        val nick: TextView = v.findViewById(R.id.tvMsgNick)
        val text: TextView = v.findViewById(R.id.tvMsgText)
        val time: TextView = v.findViewById(R.id.tvMsgTime)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

        override fun getItemCount() = messages.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val m = messages[position]
            (h.bubble.layoutParams as FrameLayout.LayoutParams).gravity = if (m.mine) Gravity.END else Gravity.START
            h.bubble.backgroundTintList = ColorStateList.valueOf(
                getColor(if (m.mine) R.color.tr_accent_container else R.color.tr_surface_high)
            )
            h.nick.visibility = if (m.mine && !m.admin) View.GONE else View.VISIBLE
            h.nick.text = if (m.admin) "${m.nickname}  ★ " + getString(R.string.chat_admin_badge) else m.nickname
            h.nick.setTextColor(getColor(if (m.admin) R.color.tr_surge else R.color.tr_accent))
            // Текст выделяется долгим нажатием — у админа это же нажатие открывает модерацию.
            val onLong = View.OnLongClickListener {
                if (isAdmin) moderate(m)
                isAdmin
            }
            h.itemView.setOnLongClickListener(onLong)
            h.text.setOnLongClickListener(onLong)
            h.text.text = m.text
            h.time.text = if (m.ts > 0) timeFmt.format(Date(m.ts)) else ""
        }
    }
}
