package com.github.nitrico.lastadapter

import android.databinding.*
import android.support.annotation.Keep
import android.support.annotation.LayoutRes
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

@Keep
class LastAdapter<T : Any> private constructor(private val list: List<T>,
                                               private val variable: Int,
                                               private val map: Map<Class<*>, Int>,
                                               private val listener: OnBindListener?)
: RecyclerView.Adapter<LastAdapter.ViewHolder>() {

    companion object {
        @JvmStatic fun <T : Any> with(list: List<T>, variable: Int) = Builder(list, variable)
    }

    @Keep
    class Builder<T : Any> internal constructor(private val list: List<T>,
                                                private val variable: Int) {
        private val map: MutableMap<Class<*>, Int> = mutableMapOf()
        private var listener: OnBindListener? = null
        inline fun <reified T : Any> map(@LayoutRes layout: Int) = map(T::class.java, layout)
        fun map(clazz: Class<*>, @LayoutRes layout: Int) = apply { map.put(clazz, layout) }
        fun onBindListener(listener: OnBindListener) = apply { this.listener = listener }
        fun build() = LastAdapter(list, variable, map, listener)
        fun into(recyclerView: RecyclerView): LastAdapter<T> {
            val adapter = build()
            recyclerView.adapter = adapter
            return adapter
        }
    }


    interface OnBindListener {
        fun onBind(item: Any, view: View, position: Int)
    }


    class ViewHolder(internal val binding: ViewDataBinding,
                     internal val variable: Int) : RecyclerView.ViewHolder(binding.root) {
        fun bindTo(item: Any, listener: OnBindListener?, pos: Int) {
            binding.setVariable(variable, item)
            binding.executePendingBindings()
            listener?.onBind(item, binding.root, pos)
        }
    }


    private val DATA_INVALIDATION = Any()
    private val onListChanged = WeakReferenceOnListChangedCallback(this)
    private var recyclerView: RecyclerView? = null
    private var inflater: LayoutInflater? = null

    override fun onCreateViewHolder(view: ViewGroup, type: Int): ViewHolder {
        if (inflater == null) inflater = LayoutInflater.from(view.context)
        val binding = DataBindingUtil.inflate<ViewDataBinding>(inflater, type, view, false)
        val holder = ViewHolder(binding, variable)
        addOnRebindCallback(binding, recyclerView, holder.adapterPosition)
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) = holder.bindTo(list[pos], listener, pos)

    override fun onBindViewHolder(holder: ViewHolder, pos: Int, payloads: MutableList<Any>?) {
        if (isForDataBinding(payloads)) holder.binding.executePendingBindings()
        else onBindViewHolder(holder, pos)
    }

    override fun getItemCount() = list.size

    override fun getItemViewType(i: Int) = map[list[i].javaClass]
            ?: throw RuntimeException("Invalid object at position: $i: ${list[i]}")

    override fun onAttachedToRecyclerView(rv: RecyclerView?) {
        if (recyclerView == null && list is ObservableList) list.addOnListChangedCallback(onListChanged)
        recyclerView = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView?) {
        if (recyclerView != null && list is ObservableList) list.removeOnListChangedCallback(onListChanged)
        recyclerView = null
    }

    private fun addOnRebindCallback(b: ViewDataBinding, rv: RecyclerView?, pos: Int) {
        b.addOnRebindCallback(object: OnRebindCallback<ViewDataBinding>() {
            override fun onPreBind(binding: ViewDataBinding) = rv != null && rv.isComputingLayout
            override fun onCanceled(binding: ViewDataBinding) {
                if (rv == null || rv.isComputingLayout) return
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos, DATA_INVALIDATION)
            }
        })
    }

    private fun isForDataBinding(payloads: List<Any>?): Boolean {
        if (payloads == null || payloads.size == 0) return false
        payloads.forEach { if (it == DATA_INVALIDATION) return false }
        return true
    }

}
