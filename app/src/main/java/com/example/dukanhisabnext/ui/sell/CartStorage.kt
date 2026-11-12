package com.example.dukanhisabnext.ui.sell

import com.example.dukanhisabnext.data.entity.ProductUnit
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything of the sale in progress that survives the app being closed by Android. */
data class SavedCart(
    val state: CartState,
    val customerId: Long?,
    val draftId: String
)

/** Where CartStore keeps the cart between app starts. */
interface CartStorage {
    /** The saved cart, or null when there is none (or it cannot be read). */
    fun load(): SavedCart?
    fun save(cart: SavedCart)
    fun delete()

    /** Nothing is kept (tests, and the default). */
    object None : CartStorage {
        override fun load(): SavedCart? = null
        override fun save(cart: SavedCart) {}
        override fun delete() {}
    }
}

/** The version of the cart file. A file of another version is ignored (the cart starts empty). */
const val CART_FILE_VERSION = 1

/**
 * The cart as a small JSON file in the app's private files folder (not in the database, not in the
 * backup). Written on every change: to a temporary file, then renamed, so a half-written file can
 * never be read. Example:
 *   {"version":1,"draftId":"…","customerId":12,"discountOn":false,"discountUnit":"TAKA",
 *    "discountText":"","deliveryOn":false,"deliveryText":"",
 *    "lines":[{"productId":3,"name":"চাল","unit":"KG","quantityText":"1.5","unitPriceText":"75"}]}
 */
class FileCartStorage(private val file: File) : CartStorage {

    override fun load(): SavedCart? = try {
        if (!file.exists()) null else cartFromJson(file.readText(Charsets.UTF_8))
    } catch (e: IOException) {
        null
    }

    @Synchronized
    override fun save(cart: SavedCart) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(cartToJson(cart), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            // The cart still works in memory; only the copy on the disk is behind.
        }
    }

    @Synchronized
    override fun delete() {
        file.delete()
        File(file.path + ".tmp").delete()
    }
}

fun cartToJson(cart: SavedCart): String {
    val s = cart.state
    val lines = JSONArray()
    for (line in s.lines) {
        lines.put(
            JSONObject()
                .put("productId", line.productId)
                .put("name", line.name)
                .put("unit", line.unit.name)
                .put("quantityText", line.quantityText)
                .put("unitPriceText", line.unitPriceText)
        )
    }
    return JSONObject()
        .put("version", CART_FILE_VERSION)
        .put("draftId", cart.draftId)
        .put("customerId", cart.customerId ?: JSONObject.NULL)
        .put("discountOn", s.discountOn)
        .put("discountUnit", s.discountUnit.name)
        .put("discountText", s.discountText)
        .put("deliveryOn", s.deliveryOn)
        .put("deliveryText", s.deliveryText)
        .put("lines", lines)
        .toString()
}

/** The cart from its file text, or null when the text is not a cart of this version. */
fun cartFromJson(text: String): SavedCart? = try {
    val o = JSONObject(text)
    if (o.optInt("version", -1) != CART_FILE_VERSION) {
        null
    } else {
        val linesJson = o.getJSONArray("lines")
        val lines = (0 until linesJson.length()).map { i ->
            val l = linesJson.getJSONObject(i)
            CartLineInput(
                productId = l.getLong("productId"),
                name = l.getString("name"),
                unit = ProductUnit.valueOf(l.getString("unit")),
                quantityText = l.getString("quantityText"),
                unitPriceText = l.getString("unitPriceText")
            )
        }
        val draftId = o.getString("draftId")
        if (draftId.isBlank()) {
            null
        } else {
            SavedCart(
                state = CartState(
                    lines = lines.distinctBy { it.productId }, // one line per product, always
                    discountOn = o.getBoolean("discountOn"),
                    discountUnit = DiscountUnit.valueOf(o.getString("discountUnit")),
                    discountText = o.getString("discountText"),
                    deliveryOn = o.getBoolean("deliveryOn"),
                    deliveryText = o.getString("deliveryText")
                ),
                customerId = if (o.isNull("customerId")) null else o.getLong("customerId"),
                draftId = draftId
            )
        }
    }
} catch (e: JSONException) {
    null
} catch (e: IllegalArgumentException) {
    null // an unknown unit or discount kind
}
