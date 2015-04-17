package com.hanhuy.android.keepshare

import android.content.{ComponentName, Intent}
import android.graphics.BitmapFactory
import android.graphics.drawable.{BitmapDrawable, LayerDrawable}
import android.os.Bundle
import com.hanhuy.android.common.AndroidConversions._

import android.app.{Activity, SearchManager}
import android.view._
import android.widget.{SearchView, BaseAdapter}
import com.hanhuy.keepassj._

import collection.JavaConverters._
import Futures._
import BrowseActivity._

/**
 * @author pfnguyen
 */
object BrowseActivity {
  val EXTRA_GROUP_ID = "keepshare.extra.GROUP_ID"
  val EXTRA_STACK = "keepshare.extra.STACK"
  def browse(a: Activity, group: PwGroup): Unit = {
    val intent = new Intent(a, classOf[BrowseActivity])
    intent.putExtra(BrowseActivity.EXTRA_GROUP_ID, KeyManager.hex(group.getUuid.getUuidBytes))
    a.startActivity(intent)
  }
  def open(a: Activity): Unit = {
    val intent = new Intent(a, classOf[BrowseActivity])
    a.startActivity(intent)
  }
}
class BrowseActivity extends AuthorizedActivity with TypedActivity {
  lazy val list = findView(TR.list)
  private var searchView = Option.empty[SearchView]
  override def onCreateOptionsMenu(menu: Menu) = {
    super.onCreateOptionsMenu(menu)
    getMenuInflater.inflate(R.menu.browse, menu)
    searchView = Option(menu.findItem(R.id.menu_search).getActionView.asInstanceOf[SearchView])
    searchView foreach { search =>
      search.setIconifiedByDefault(getResources.getBoolean(R.bool.is_phone))
      search.setSearchableInfo(
        this.systemService[SearchManager].getSearchableInfo(
          new ComponentName(getPackageName, "com.hanhuy.android.keepshare.SearchableActivity")))
    }
    true
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.browse)
  }

  override def onOptionsItemSelected(item: MenuItem) = item.getItemId match {
    case android.R.id.home =>
      onBackPressed()
      true
    case _ => super.onOptionsItemSelected(item)
  }

  override def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    setIntent(intent)
  }


  override def onBackPressed() = {
//    navigateUp()
    val shouldBack = searchView exists (_.isIconified)
    searchView foreach (_.setIconified(true))
    if (shouldBack) {
      super.onBackPressed()
      if (Option(getIntent) exists (_.hasExtra(EXTRA_GROUP_ID)))
        overridePendingTransition(0, 0)
    }
  }

  private def navigateUp(): Unit = {
//    stack match {
//      case x :: xs =>
//        stack = xs
//        navigateTo(xs.headOption)
//      case Nil =>
//        Option(getIntent) foreach { _.putExtra(EXTRA_GROUP_ID, null: String) }
//        finish()
//    }
//    for {
//      intent <- Option(getIntent)
//      extras <- Option(intent.getExtras)
//      head   <- stack.headOption
//    } {
//      intent.putExtra(EXTRA_GROUP_ID, head.ToHexString)
//    }
  }

  private def navigateTo(groupId: Option[PwUuid]): Unit = {
    database.onSuccessMain { case db =>
      val root = db.getRootGroup
      val group = groupId map { id =>
        root.FindGroup(id, true) } getOrElse root
      val ab = getSupportActionBar
      ab.setSubtitle(group.getName)
      if (PwUuid.Zero == group.getCustomIconUuid) {
        val bm = BitmapFactory.decodeResource(getResources, Database.Icons(group.getIconId.ordinal))
        val bd = new BitmapDrawable(getResources, bm)
        bd.setGravity(Gravity.CENTER)
        val layers = new LayerDrawable(Array(bd, getDrawable(R.drawable.logo_frame)))
        ab.setIcon(layers)
        ab.setDisplayShowHomeEnabled(true)
        ab.setDisplayHomeAsUpEnabled(group != root)
      }
      val groups = group.GetGroups(false).asScala.toList
      val entries = group.GetEntries(false).asScala.toList

      val adapter = new GroupAdapter(groups, entries)
      list.setDividerHeight(0)
      list.setAdapter(adapter)
      list.onItemClick { row =>
        val item = adapter.getItem(row)
        item.left foreach { grp =>
          browse(this, grp)
          overridePendingTransition(0, 0)
        }
        item.right foreach { entry =>
          EntryViewActivity.show(this, entry)
          overridePendingTransition(R.anim.slide_in_right,
            R.anim.slide_out_left)
        }
      }
    }
  }

//  private var stack = List.empty[PwUuid]

  private def handleIntent(): Unit = {
    val groupId = for {
      intent <- Option(getIntent)
      extras <- Option(intent.getExtras)
      id     <- Option(extras.getString(BrowseActivity.EXTRA_GROUP_ID))
    } yield new PwUuid(KeyManager.bytes(id))

//    for {
//      id   <- groupId
//      head <- stack.headOption orElse Some(PwUuid.Zero)
//      root <- Database.rootGroupid orElse Some(PwUuid.Zero)
//    } {
//      if (head != id && id != root)
//        stack = id :: stack
//    }

//    navigateTo(stack.headOption)
    navigateTo(groupId)
  }

  override def onStart() = {
    super.onStart()
  }

  override def onResume() = {
    super.onResume()
    handleIntent()
  }

  override def onSaveInstanceState(outState: Bundle) = {
    super.onSaveInstanceState(outState)
//    outState.putStringArray(EXTRA_STACK,
//      stack.map (u => u.ToHexString()).toArray)
  }

  override def onRestoreInstanceState(savedInstanceState: Bundle) = {
    super.onRestoreInstanceState(savedInstanceState)
//    Option(savedInstanceState.getStringArray(EXTRA_STACK)) foreach { ss =>
//      stack = ss map (s => new PwUuid(KeyManager.bytes(s))) toList
//    }
  }

  class GroupAdapter(groups: Seq[PwGroup], entries: Seq[PwEntry]) extends BaseAdapter {
    import TypedResource._
    val data = (groups map(Left(_))) ++ (entries map(Right(_)))
    override def getItemId(position: Int) = data(position).fold(Database.getId, Database.getId)
    override def getCount = data.size
    override def getView(position: Int, convertView: View, parent: ViewGroup) = {
      val view = if (convertView == null) {
        getLayoutInflater.inflate(TR.layout.browse_pwgroup_item, parent, false)
      } else {
        convertView
      }
      val item = getItem(position)

      view.findView(TR.name).setText(item.fold(
        _.getName, _.getStrings.ReadSafe(PwDefs.TitleField)))
      val folder = view.findView(TR.folder_image)
      val icon = view.findView(TR.entry_image)

      item.left foreach { group =>
        folder.setVisibility(View.VISIBLE)
        if (PwUuid.Zero == group.getCustomIconUuid)
          icon.setImageResource(Database.Icons(group.getIconId.ordinal))
      }
      item.right foreach { entry =>
        folder.setVisibility(View.INVISIBLE)
        if (PwUuid.Zero == entry.getCustomIconUuid)
          icon.setImageResource(Database.Icons(entry.getIconId.ordinal))
      }

      view
    }
    override def getItem(position: Int) = data(position)
    override def getItemViewType(position: Int) = if (data(position).isLeft) 0 else 1
    override def getViewTypeCount = 2
  }
}