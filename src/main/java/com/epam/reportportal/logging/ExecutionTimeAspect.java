/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.logging;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Andrei Piankouski
 */
@Aspect
@Component
public class ExecutionTimeAspect {

  public static final Logger LOGGER = LoggerFactory.getLogger(ExecutionTimeAspect.class);

  @Around("@annotation(annotation)")
  public Object executionTime(ProceedingJoinPoint point, SchedulerLock annotation) throws Throwable {
    String name = annotation.name();
    long startTime = System.currentTimeMillis();
    LOGGER.info("Job {} has been started.", name);
    Object object = point.proceed();
    long endtime = System.currentTimeMillis();

    LOGGER.info("Job {} has been finished. Time taken for Execution is : {} ms", name, (endtime-startTime));
    return object;
  }
}