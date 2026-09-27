package com.company.admob.sample.xml.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.company.admob.sample.xml.R
import com.company.admob.sample.xml.databinding.FragmentFeedBinding
import com.company.admob.sample.xml.databinding.ItemFeedAdBinding
import com.company.admob.sample.xml.databinding.ItemFeedArticleBinding

/**
 * The list pattern for native ads: ad rows bind their position's [com.company.admob.views.NativeAdHolder] (owned by
 * [FeedViewModel]) to the recycled row container, with the Fragment's view lifecycle. Binding again re-renders the
 * same ad; it never loads a new one.
 */
class FeedFragment : Fragment() {
    private val viewModel: FeedViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentFeedBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentFeedBinding.bind(view)
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = FeedAdapter(viewModel, viewLifecycleOwner)
    }
}

private class FeedAdapter(
    private val viewModel: FeedViewModel,
    private val viewLifecycleOwner: LifecycleOwner,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private class ArticleHolder(val binding: ItemFeedArticleBinding) : RecyclerView.ViewHolder(binding.root)

    private class AdHolder(val binding: ItemFeedAdBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount(): Int = viewModel.rows.size

    override fun getItemViewType(position: Int): Int = when (viewModel.rows[position]) {
        is FeedRow.Article -> TYPE_ARTICLE
        is FeedRow.Ad -> TYPE_AD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_AD) {
            AdHolder(ItemFeedAdBinding.inflate(inflater, parent, false))
        } else {
            ArticleHolder(ItemFeedArticleBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = viewModel.rows[position]) {
            is FeedRow.Article -> (holder as ArticleHolder).binding.title.text =
                holder.itemView.context.getString(R.string.feed_article, row.index + 1)
            // No layout: the ads file's style for feed_native (small) picks the template.
            is FeedRow.Ad -> viewModel.adHolder(row.slot).bindTo((holder as AdHolder).binding.adContainer, viewLifecycleOwner)
        }
    }

    private companion object {
        const val TYPE_ARTICLE = 0
        const val TYPE_AD = 1
    }
}
