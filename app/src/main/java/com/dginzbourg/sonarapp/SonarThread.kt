package com.dginzbourg.sonarapp

import java.lang.Exception

class SonarThread(target: Runnable) : Thread(target) {

    override fun run() {
        try {
            super.run()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}