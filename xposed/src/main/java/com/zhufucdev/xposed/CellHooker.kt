@file:Suppress("DEPRECATION")

package com.zhufucdev.xposed

import android.telephony.*
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.stub.CellMoment
import com.zhufucdev.stub.Toggle
import java.util.concurrent.Executor

class CellHooker(private val scheduler: XposedScheduler) : YukiBaseHooker() {
    private val listeners = mutableSetOf<(CellMoment) -> Unit>()

    var toggle = Toggle.PRESENT

    override fun onHook() {
        classOf<TelephonyManager>().apply {
            method {
                name = "getCellLocation"
                emptyParam()
                returnType(classOf<CellLocation>())
            }
                .hook {
                    after {
                        if (!scheduler.isWorking || toggle == Toggle.NONE)
                            return@after
                        result = if (toggle == Toggle.BLOCK)
                            null
                        else {
                            scheduler.cells.cellLocation()
                        }
                    }
                }
            method {
                name = "getAllCellInfo"
                emptyParam()
                returnType(classOf<List<CellInfo>>())
            }
                .hook {
                    after {
                        if (!scheduler.isWorking || toggle == Toggle.NONE)
                            return@after
                        result =
                            if (toggle == Toggle.BLOCK)
                                null
                            else
                                scheduler.cells.cell.takeIf { it.isNotEmpty() }
                    }
                }
            method {
                name = "getNeighboringCellInfo"
                emptyParam()
                returnType = classOf<List<NeighboringCellInfo>>()
            }
                .hook {
                    after {
                        if (!scheduler.isWorking || toggle == Toggle.NONE)
                            return@after
                        result =
                            if (toggle == Toggle.BLOCK) {
                                null
                            } else {
                                scheduler.cells.neighboringInfo().takeIf { it.isNotEmpty() }
                            }
                    }
                }
            method {
                name = "listen"
                param(classOf<PhoneStateListener>(), IntType)
                returnType = UnitType
            }
                .hook {
                    replaceUnit {
                        if (!scheduler.isWorking || toggle == Toggle.NONE) {
                            callOriginal()
                            return@replaceUnit
                        }

                        if (toggle == Toggle.BLOCK)
                            return@replaceUnit

                        val listener = args(0).cast<PhoneStateListener>()
                        val mode = args(1).int()
                        addListener {
                            listener?.treatWith(it, mode)
                        }
                    }
                }

            method {
                name = "registerTelephonyCallback"
                param(classOf<Executor>(), classOf<TelephonyCallback>())
                returnType = UnitType
            }
                .hook {
                    replaceUnit {
                        if (!scheduler.isWorking || toggle == Toggle.NONE) {
                            callOriginal()
                            return@replaceUnit
                        }

                        if (toggle == Toggle.BLOCK)
                            return@replaceUnit

                        val executor = args(0).cast<Executor>()
                        val callback = args(1).cast<TelephonyCallback>()
                        addListener {
                            executor?.execute {
                                callback?.treatWith(scheduler.cells)
                            }
                        }
                    }
                }

            method {
                name = "getPhoneCount"
                emptyParam()
                returnType = IntType
            }
                .hook {
                    replaceAny {
                        if (!scheduler.isWorking || toggle == Toggle.NONE) {
                            callOriginal()
                        } else if (toggle == Toggle.BLOCK) {
                            0
                        } else {
                            1
                        }
                    }
                }

            method {
                name = "getActiveModemCount"
                emptyParam()
                returnType = IntType
            }
                .hook {
                    replaceAny {
                        if (!scheduler.isWorking || toggle == Toggle.NONE) {
                            callOriginal()
                        } else if (toggle == Toggle.BLOCK) {
                            0
                        } else {
                            1
                        }
                    }
                }
        }
    }

    private fun addListener(l: (CellMoment) -> Unit) {
        l(scheduler.cells)
        listeners.add(l)
    }

    fun raise(moment: CellMoment) {
        listeners.forEach {
            it(moment)
        }
    }
}